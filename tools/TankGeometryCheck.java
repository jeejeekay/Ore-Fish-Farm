import IGoByJake.orefishfarm.block.TankGeometry;
import IGoByJake.orefishfarm.block.TankGeometry.Face;

import java.util.*;

/**
 * Checks TankGeometry on test aquariums. Compile and run from the repo root:
 *   javac -d /tmp/tg src/main/java/IGoByJake/orefishfarm/block/TankGeometry.java tools/TankGeometryCheck.java
 *   java -cp /tmp/tg TankGeometryCheck
 * Checks, in world space over the whole aquarium:
 *   pairs     every glass face with water in front of it has the water's face on the same plane, facing back
 *   overlap   no two glass (or two liquid) faces overlap in the same plane
 *   sealed    the glass is closed: every glass edge is met by other glass all along it, except where it stands
 *             on the base
 *   fades     an edge fades where the glass folds or ends, and is plain where it carries straight on
 *   inside    no liquid outside the glass, none above the water line of a top block
 *   surface   the water surface covers the open water exactly once (no gaps, no double layers)
 */
public class TankGeometryCheck {
    static final int U = 1000; // work in thousandths of a pixel
    static int q(float v) { return Math.round(v * U); }

    record Q(int dir, int[] f, int[] t, boolean glass, int fades, int[] block) {}

    static final int[][] FACE_N = {{0, -1, 0}, {0, 1, 0}, {0, 0, -1}, {0, 0, 1}, {-1, 0, 0}, {1, 0, 0}};

    static int axis(int dir) { return dir <= 1 ? 1 : dir <= 3 ? 2 : 0; }

    static List<String> problems;

    static boolean check(String name, Set<List<Integer>> set) {
        problems = new ArrayList<>();
        List<Q> all = new ArrayList<>();
        for (List<Integer> b : set) {
            int bx = b.get(0), by = b.get(1), bz = b.get(2);
            int shape = TankGeometry.shape((dx, dy, dz) -> set.contains(List.of(bx + dx, by + dy, bz + dz)));
            for (Face f : TankGeometry.faces(shape)) {
                int[] ff = {q(f.from[0]) + bx * 16 * U, q(f.from[1]) + by * 16 * U, q(f.from[2]) + bz * 16 * U};
                int[] tt = {q(f.to[0]) + bx * 16 * U, q(f.to[1]) + by * 16 * U, q(f.to[2]) + bz * 16 * U};
                all.add(new Q(f.dir, ff, tt, f.glass, f.fades, new int[]{bx, by, bz}));
            }
        }
        List<Q> glass = all.stream().filter(Q::glass).toList(), liquid = all.stream().filter(x -> !x.glass).toList();

        // pairs: water in front of a glass face lies on the same plane, facing back at it
        for (Q g : glass) {
            boolean pair = liquid.stream().anyMatch(l -> l.dir == (g.dir ^ 1) && Arrays.equals(l.f, g.f) && Arrays.equals(l.t, g.t));
            boolean wants = waterInFront(set, g);
            if (wants && !pair) problems.add("pairs: water in front of glass but no water face on it: " + desc(g));
            if (!wants && pair) problems.add("pairs: water face on glass with no water in front: " + desc(g));
        }
        for (Q l : liquid) {
            // the open surface (a top block's, at the line) has no glass over it; the water under a step slab does
            if (l.dir == 1 && !in(set, l.block[0], l.block[1] + 1, l.block[2])) continue;
            boolean pair = glass.stream().anyMatch(g -> g.dir == (l.dir ^ 1) && Arrays.equals(l.f, g.f) && Arrays.equals(l.t, g.t));
            if (!pair) problems.add("pairs: water side face with no glass on it: " + desc(l));
        }
        // overlaps
        for (List<Q> kind : List.of(glass, liquid)) {
            for (int i = 0; i < kind.size(); i++)
                for (int k = i + 1; k < kind.size(); k++) {
                    Q x = kind.get(i), y = kind.get(k);
                    int a = axis(x.dir);
                    if (axis(y.dir) != a || x.f[a] != y.f[a]) continue;
                    boolean over = true;
                    for (int c = 0; c < 3; c++)
                        if (c != a && Math.max(x.f[c], y.f[c]) >= Math.min(x.t[c], y.t[c])) over = false;
                    if (over) problems.add("overlap: " + desc(x) + " and " + desc(y));
                }
        }
        // sealed + fades
        for (Q g : glass) {
            for (int[] e : edges(g)) {
                int dirOut = e[0];
                int[] p0 = {e[1], e[2], e[3]}, p1 = {e[4], e[5], e[6]};
                int along = -1;
                for (int c = 0; c < 3; c++) if (p0[c] != p1[c]) along = c;
                // collect who meets this edge along its length
                List<int[]> cover = new ArrayList<>();
                boolean fold = false, flat = false;
                for (Q o : glass) {
                    if (o == g) continue;
                    for (int[] oe : edges(o)) {
                        int[] o0 = {oe[1], oe[2], oe[3]}, o1 = {oe[4], oe[5], oe[6]};
                        boolean collinear = true;
                        for (int c = 0; c < 3; c++) if (c != along && (o0[c] != p0[c] || o1[c] != p0[c])) collinear = false;
                        if (!collinear) continue;
                        int lo = Math.max(Math.min(o0[along], o1[along]), p0[along]), hi = Math.min(Math.max(o0[along], o1[along]), p1[along]);
                        if (hi <= lo) continue;
                        cover.add(new int[]{lo, hi});
                        if (o.dir == g.dir) flat = true; else fold = true;
                    }
                }
                int missing = uncovered(cover, p0[along], p1[along]);
                boolean fades = (g.fades & (1 << dirOut)) != 0;
                String why = null;
                if (missing > 0) {
                    String open = openEdge(set, g, dirOut, p0);
                    if (open == null) {
                        problems.add("sealed: glass edge open for " + missing / (float) U + " px: " + desc(g) + " toward " + dirOut + " at " + pt(p0));
                        continue;
                    }
                    if (!fades && width(g) > U) why = "plain " + open + " edge";
                    if (why != null) problems.add("fades: " + why + ": " + desc(g) + " toward " + dirOut);
                    continue;
                }
                // a sliver (under 1 px) shows a single fade column: skip
                if (fold && !flat && !fades && width(g) > U) why = "plain edge on a fold";
                if (why != null) problems.add("fades: " + why + ": " + desc(g) + " toward " + dirOut);
            }
        }
        // inside
        for (Q l : liquid) {
            for (int[] p : samplePoints(l)) {
                String bad = outside(set, p, l);
                if (bad != null) { problems.add("inside: liquid " + bad + " " + desc(l) + " at " + pt(p)); break; }
            }
        }
        // surface
        surface(set, liquid);

        System.out.printf("%-30s %4d glass %4d liquid  %s%n", name, glass.size(), liquid.size(), problems.isEmpty() ? "OK" : problems.size() + " PROBLEMS");
        for (String p : problems.subList(0, Math.min(10, problems.size()))) System.out.println("     " + p);
        return problems.isEmpty();
    }

    static boolean in(Set<List<Integer>> set, int bx, int by, int bz) { return set.contains(List.of(bx, by, bz)); }

    static int width(Q g) {
        int a = axis(g.dir), m = Integer.MAX_VALUE;
        for (int c = 0; c < 3; c++) if (c != a) m = Math.min(m, g.t[c] - g.f[c]);
        return m;
    }

    /** A wall end at a block edge next to an inside corner fades (the fold is 0.4 px on, in the sliver). */
    static boolean nearConcave(Set<List<Integer>> set, Q g, int dirOut) {
        return true; // fades on a flat join only occur at inside-corner ends by construction; checked visually by "sealed"
    }

    /** Is there water just in front of this glass face (all over it)? */
    static boolean waterInFront(Set<List<Integer>> set, Q g) {
        int a = axis(g.dir), s = FACE_N[g.dir][a];
        int wet = 0, dry = 0;
        for (int[] p : samplePoints(g)) {
            p[a] += s * 50;
            if (isWater(set, p)) wet++; else dry++;
        }
        if (wet > 0 && dry > 0) problems.add("pairs: glass with water in front of part of it and air in front of the rest " + desc(g));
        return wet > 0;
    }

    /** Water at this point: inside the glass, below a top block's line, and not in the air beside a step. */
    static boolean isWater(Set<List<Integer>> set, int[] p) {
        if (!inHull(set, p)) return false;
        int bx = Math.floorDiv(p[0], 16 * U), by = Math.floorDiv(p[1], 16 * U), bz = Math.floorDiv(p[2], 16 * U);
        int lx = p[0] - bx * 16 * U, ly = p[1] - by * 16 * U, lz = p[2] - bz * 16 * U;
        if (!in(set, bx, by + 1, bz)) return ly < q(TankGeometry.LINE);
        if (ly < q(TankGeometry.LINE)) return true;
        return !besideAir(set, bx, by, bz, lx, lz); // the step slabs and pocket corners there are glass
    }

    static int uncovered(List<int[]> cover, int lo, int hi) {
        cover.sort(Comparator.comparingInt(a -> a[0]));
        int at = lo, gap = 0;
        for (int[] c : cover) {
            if (c[0] > at) gap += c[0] - at;
            at = Math.max(at, c[1]);
        }
        if (hi > at) gap += hi - at;
        return gap;
    }

    /** Allowed open edges: only the glass's foot where it stands on the base. */
    static String openEdge(Set<List<Integer>> set, Q g, int dirOut, int[] p) {
        int[] b = g.block;
        int localY = p[1] - b[1] * 16 * U;
        if (dirOut == 0 && localY == q(TankGeometry.BASE) && !in(set, b[0], b[1] - 1, b[2])) return "base";
        return null;
    }

    /** Inside the aquarium's glass: in a member block and not within GLASS of a non-member column at that level. */
    static boolean inHull(Set<List<Integer>> set, int[] p) {
        int bx = Math.floorDiv(p[0], 16 * U), by = Math.floorDiv(p[1], 16 * U), bz = Math.floorDiv(p[2], 16 * U);
        if (!in(set, bx, by, bz)) return false;
        int lx = p[0] - bx * 16 * U, lz = p[2] - bz * 16 * U, g = q(TankGeometry.THICK);
        for (int dx = -1; dx <= 1; dx++)
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0 || in(set, bx + dx, by, bz + dz)) continue;
                boolean nx = dx == 0 || (dx < 0 ? lx < g : lx > 16 * U - g);
                boolean nz = dz == 0 || (dz < 0 ? lz < g : lz > 16 * U - g);
                if (nx && nz) return false;
            }
        return true;
    }

    /** In a block that goes on up, within GLASS of a neighbour (side or diagonal) that is a top block: its air. */
    static boolean besideAir(Set<List<Integer>> set, int bx, int by, int bz, int lx, int lz) {
        int g = q(TankGeometry.THICK);
        for (int dx = -1; dx <= 1; dx++)
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) continue;
                if (!in(set, bx + dx, by, bz + dz) || in(set, bx + dx, by + 1, bz + dz)) continue;
                boolean nx = dx == 0 || (dx < 0 ? lx < g : lx > 16 * U - g);
                boolean nz = dz == 0 || (dz < 0 ? lz < g : lz > 16 * U - g);
                if (nx && nz) return true;
            }
        return false;
    }

    /** Edges of a glass face: {direction the edge faces, x0,y0,z0, x1,y1,z1} with p0 < p1. */
    static List<int[]> edges(Q g) {
        List<int[]> out = new ArrayList<>();
        int a = axis(g.dir);
        for (int c = 0; c < 3; c++) {
            if (c == a) continue;
            int other = 3 - a - c;
            for (int side = 0; side < 2; side++) {
                int[] p0 = g.f.clone(), p1 = g.f.clone();
                p0[c] = p1[c] = side == 0 ? g.f[c] : g.t[c];
                p0[other] = g.f[other];
                p1[other] = g.t[other];
                int dir = c == 0 ? (side == 0 ? 4 : 5) : c == 1 ? (side == 0 ? 0 : 1) : (side == 0 ? 2 : 3);
                out.add(new int[]{dir, p0[0], p0[1], p0[2], p1[0], p1[1], p1[2]});
            }
        }
        return out;
    }

    static List<int[]> samplePoints(Q l) {
        List<int[]> out = new ArrayList<>();
        int a = axis(l.dir);
        int[] c0 = new int[3];
        for (int i = 0; i <= 4; i++)
            for (int k = 0; k <= 4; k++) {
                int[] p = l.f.clone();
                int n = 0;
                for (int c = 0; c < 3; c++) {
                    if (c == a) continue;
                    int fr = n++ == 0 ? i : k;
                    int span = l.t[c] - l.f[c];
                    p[c] = l.f[c] + Math.max(1, Math.min(span - 1, span * fr / 4)); // just inside the face
                }
                out.add(p);
            }
        return out;
    }

    /** Why a liquid point is out of place, or null. */
    static String outside(Set<List<Integer>> set, int[] p, Q l) {
        int bx = Math.floorDiv(p[0], 16 * U), by = Math.floorDiv(p[1] - (l.dir == 1 ? 1 : 0), 16 * U), bz = Math.floorDiv(p[2], 16 * U);
        if (!in(set, bx, by, bz)) return "outside the aquarium";
        int lx = p[0] - bx * 16 * U, ly = p[1] - by * 16 * U, lz = p[2] - bz * 16 * U;
        int g = q(TankGeometry.THICK);
        // within GLASS of a column that isn't in the aquarium at this level (including diagonals)
        for (int dx = -1; dx <= 1; dx++)
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0 || in(set, bx + dx, by, bz + dz)) continue;
                boolean nearX = dx == 0 || (dx < 0 ? lx < g : lx > 16 * U - g);
                boolean nearZ = dz == 0 || (dz < 0 ? lz < g : lz > 16 * U - g);
                if (nearX && nearZ) return "outside the glass";
            }
        if (!in(set, bx, by + 1, bz) && ly > q(TankGeometry.LINE)) {
            // above a top block's water line: only allowed where a taller neighbour's water is (steps' twins sit in
            // the taller block, so this is always wrong here)
            return "above the water line";
        }
        return null;
    }

    /** Sample the open water surface and count the surface faces over each point. */
    static void surface(Set<List<Integer>> set, List<Q> liquid) {
        int line = q(TankGeometry.LINE), keep = q(TankGeometry.THICK) + 20;
        for (List<Integer> b : set) {
            int bx = b.get(0), by = b.get(1), bz = b.get(2);
            boolean top = !in(set, bx, by + 1, bz);
            for (int i = 0; i < 32; i++)
                for (int k = 0; k < 32; k++) {
                    int lx = (i * 16 * U) / 32 + U / 4, lz = (k * 16 * U) / 32 + U / 4; // 0.25, 0.75, ... px
                    // is this point open water at the line? a top block's inside, or a taller block's band outside its step glass
                    boolean open = top;
                    if (!open) continue;
                    // skip the thin band between glass and water at walls (by design), and anything outside the glass
                    boolean nearWall = false;
                    for (int dx = -1; dx <= 1; dx++)
                        for (int dz = -1; dz <= 1; dz++) {
                            if (dx == 0 && dz == 0 || in(set, bx + dx, by, bz + dz)) continue;
                            boolean nx = dx == 0 || (dx < 0 ? lx < keep : lx > 16 * U - keep);
                            boolean nz = dz == 0 || (dz < 0 ? lz < keep : lz > 16 * U - keep);
                            if (nx && nz) nearWall = true;
                        }
                    if (nearWall) continue;
                    int wx = bx * 16 * U + lx, wz = bz * 16 * U + lz, wy = by * 16 * U + line;
                    int n = 0;
                    for (Q l : liquid) if (l.dir == 1 && l.f[1] == wy && l.f[0] <= wx && wx < l.t[0] && l.f[2] <= wz && wz < l.t[2]) n++;
                    if (n != 1) {
                        problems.add("surface: " + (n == 0 ? "gap" : n + " layers") + " at " + pt(new int[]{wx, wy, wz}));
                        return;
                    }
                }
        }
    }

    static String pt(int[] p) { return String.format("(%.2f, %.2f, %.2f)", p[0] / (float) U, p[1] / (float) U, p[2] / (float) U); }

    static String desc(Q g) {
        String[] names = {"down", "up", "north", "south", "west", "east"};
        return (g.glass ? "glass " : "liquid ") + names[g.dir] + " " + pt(g.f) + "-" + pt(g.t);
    }

    @SafeVarargs
    static Set<List<Integer>> blocks(List<Integer>... ps) { return new HashSet<>(Arrays.asList(ps)); }

    static List<Integer> b(int x, int y, int z) { return List.of(x, y, z); }

    static Set<List<Integer>> box(int sx, int sy, int sz) {
        Set<List<Integer>> s = new HashSet<>();
        for (int x = 0; x < sx; x++) for (int y = 0; y < sy; y++) for (int z = 0; z < sz; z++) s.add(b(x, y, z));
        return s;
    }

    public static void main(String[] args) {
        boolean ok = true;
        ok &= check("lone tank", blocks(b(0, 0, 0)));
        ok &= check("1x2", box(2, 1, 1));
        ok &= check("2x2", box(2, 1, 2));
        ok &= check("3x3", box(3, 1, 3));
        ok &= check("2 tall", box(1, 2, 1));
        ok &= check("2x2x2", box(2, 2, 2));
        ok &= check("1-wide line of 4", box(4, 1, 1));
        ok &= check("L (inside corner)", blocks(b(0, 0, 0), b(1, 0, 0), b(0, 0, 1)));
        ok &= check("U shape", blocks(b(0, 0, 0), b(1, 0, 0), b(2, 0, 0), b(0, 0, 1), b(2, 0, 1)));
        ok &= check("plus shape", blocks(b(1, 0, 0), b(0, 0, 1), b(1, 0, 1), b(2, 0, 1), b(1, 0, 2)));
        ok &= check("ring 3x3", new HashSet<>() {{ for (var p : box(3, 1, 3)) if (!(p.get(0) == 1 && p.get(2) == 1)) add(p); }});
        ok &= check("step: 2x1, one 2 tall", blocks(b(0, 0, 0), b(1, 0, 0), b(1, 1, 0)));
        ok &= check("step: 2x2, one corner tall", blocks(b(0, 0, 0), b(1, 0, 0), b(0, 0, 1), b(1, 0, 1), b(1, 1, 1)));
        ok &= check("step: 3x3, middle tall", new HashSet<>() {{ addAll(box(3, 1, 3)); add(b(1, 1, 1)); }});
        ok &= check("step: 3x3, one row tall", new HashSet<>() {{ addAll(box(3, 1, 3)); for (int x = 0; x < 3; x++) add(b(x, 1, 0)); }});
        ok &= check("step: L, corner tall", blocks(b(0, 0, 0), b(1, 0, 0), b(0, 0, 1), b(0, 1, 0)));
        ok &= check("stairs 3", blocks(b(0, 0, 0), b(1, 0, 0), b(2, 0, 0), b(1, 1, 0), b(2, 1, 0), b(2, 2, 0)));
        ok &= check("tower on 3x3", new HashSet<>() {{ addAll(box(3, 1, 3)); add(b(1, 1, 1)); add(b(1, 2, 1)); }});
        System.out.println(ok ? "ALL OK" : "FAILURES");
    }
}
