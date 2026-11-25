package IGoByJake.orefishfarm.block;

import java.util.ArrayList;
import java.util.List;

/**
 * Every glass and liquid face of one aquarium block, worked out from which neighbours are in the same aquarium.
 * Plain Java with no Minecraft types, so it can be tested on its own (tools/TankGeometryCheck.java); the client's
 * TankExtrasModel bakes what this returns. The server works out each block's shape bits ({@link #shape}).
 *
 * <h2>Layers</h2>
 * Glass goes on Minecraft's tripwire layer, liquid on the translucent layer. Tripwire always draws after
 * translucent, so glass is always over the liquid, from any angle, without relying on Minecraft's occasional
 * re-sorting of see-through faces.
 *
 * <h2>Layout, in pixels of the block</h2>
 * <ul>
 *   <li>Base: 0..3 (the block model, solid) when there is no aquarium block below.</li>
 *   <li>Walls: 1 px ({@link #THICK}) slabs of glass on each side with no aquarium block beyond: the outer face on the
 *       block's own face (skipped where a solid block covers it, so nothing z-fights), the inner face 1 px in, and a
 *       1 px strip on top where nothing is above, which is the outline seen from above. Walls meet at the block's
 *       corners, so inside corners and steps need no gap-filling beyond 1 px corner pieces.</li>
 *   <li>Liquid: its side faces lie on the walls' inner faces, facing out (only one of the two can ever face the
 *       camera), up to the water line {@link #LINE} in a block with nothing above it, or 16 under one.</li>
 *   <li>Steps (a block above this one but not above the neighbour on that side): a 1 px slab on that side from the
 *       neighbour's water line up, with its underside; the neighbour's water surface meets it.</li>
 *   <li>Inside corners (aquarium on two sides but not diagonally between them) and pocket corners (both sides go on
 *       up but the diagonal doesn't): 1 px corner pieces that carry the walls round.</li>
 * </ul>
 * The glass fades in (never solid) where its surface folds: corners, the top, the foot on the base, steps' edges;
 * the 1 px strips (top outline, steps' undersides) are faded all over. Where it carries on it is plain.
 */
public final class TankGeometry {
    private TankGeometry() {}

    // ------------------------------------------------------------------ shape bits (see Aquarium.shape)

    /** Horizontal sides in this order everywhere: north, east, south, west. */
    public static final int N = 0, E = 1, S = 2, W = 3;
    /** The neighbour on side i is in the aquarium: bit i. */
    public static int joined(int i) { return 1 << i; }
    public static final int UP = 1 << 4, DOWN = 1 << 5;
    /** The block diagonally between sides a and b is in the aquarium: bits 6..9 for ne, nw, se, sw. */
    public static int diagonal(int a, int b) {
        int lo = Math.min(a, b), hi = Math.max(a, b);
        if (lo == N && hi == E) return 1 << 6;
        if (lo == N && hi == W) return 1 << 7;
        if (lo == E && hi == S) return 1 << 8;
        return 1 << 9; // S, W
    }
    /** The block above the neighbour on side i is in the aquarium: bit 10 + i. */
    public static int joinedAbove(int i) { return 1 << (10 + i); }
    /** The block above the diagonal between sides a and b is in the aquarium: bits 14..17 (ne, nw, se, sw). */
    public static int diagonalAbove(int a, int b) { return diagonal(a, b) << 8; }
    /** Set on every worked-out shape, so 0 means "not known yet". */
    public static final int KNOWN = 1 << 20;

    /** Whether the block at this offset from ours is in the same aquarium. */
    public interface Inside {
        boolean at(int dx, int dy, int dz);
    }

    private static final int[][] STEP = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}}; // n, e, s, w as (dx, dz)

    /** The shape bits of a block, from which of its neighbours are in its aquarium. */
    public static int shape(Inside in) {
        int s = KNOWN;
        for (int i = 0; i < 4; i++) {
            if (in.at(STEP[i][0], 0, STEP[i][1])) s |= joined(i);
            if (in.at(STEP[i][0], 1, STEP[i][1])) s |= joinedAbove(i);
        }
        if (in.at(0, 1, 0)) s |= UP;
        if (in.at(0, -1, 0)) s |= DOWN;
        int[][] diag = {{N, E}, {N, W}, {S, E}, {S, W}};
        for (int[] c : diag) {
            if (in.at(STEP[c[0]][0] + STEP[c[1]][0], 0, STEP[c[0]][1] + STEP[c[1]][1])) s |= diagonal(c[0], c[1]);
            if (in.at(STEP[c[0]][0] + STEP[c[1]][0], 1, STEP[c[0]][1] + STEP[c[1]][1])) s |= diagonalAbove(c[0], c[1]);
        }
        return s;
    }

    // ------------------------------------------------------------------ layout

    public static final float THICK = 1, BASE = 3, LINE = 13;

    /** Face directions, as Minecraft's Direction ordinals: down, up, north, south, west, east. */
    public static final int F_DOWN = 0, F_UP = 1, F_NORTH = 2, F_SOUTH = 3, F_WEST = 4, F_EAST = 5;
    private static final int[] SIDE_FACE = {F_NORTH, F_EAST, F_SOUTH, F_WEST};
    /** Fade on every edge (the 1 px strips). */
    public static final int ALL = 0b111111;

    /** One face: a flat box (from == to on the face's axis), what it is, and for glass which edges fade. */
    public static final class Face {
        public final int dir;
        public final float[] from, to;
        public final boolean glass;
        /** Glass only: fading edges, as directions (Minecraft Direction ordinals) the edge faces. */
        public final int fades;
        /** Skip this face when a solid block covers that side of the block (the walls' outer faces). */
        public final boolean cull;

        Face(int dir, float[] from, float[] to, boolean glass, int fades, boolean cull) {
            this.dir = dir;
            this.from = from;
            this.to = to;
            this.glass = glass;
            this.fades = fades;
            this.cull = cull;
        }

        @Override
        public String toString() {
            return (glass ? "glass " : "liquid ") + dir + " " + java.util.Arrays.toString(from) + "->" + java.util.Arrays.toString(to);
        }
    }

    private static boolean has(int shape, int bit) { return (shape & bit) != 0; }

    private static boolean j(int shape, int i) { return has(shape, joined(i)); }

    private static int opposite(int i) { return (i + 2) % 4; }

    /** The two sides beside side i (its ends): the lower-coordinate one first. */
    private static int[] ends(int i) {
        return (i == N || i == S) ? new int[]{W, E} : new int[]{N, S};
    }

    private static int axisOf(int i) { return (i == E || i == W) ? 0 : 2; }
    private static boolean plusSide(int i) { return i == E || i == S; }
    private static int faceBit(int side) { return 1 << SIDE_FACE[side]; }

    /** The coordinate `depth` in from side i's block edge. */
    private static float at(int i, float depth) { return plusSide(i) ? 16 - depth : depth; }

    /** The band within THICK of side i's edge. */
    private static float[] band(int i) {
        return plusSide(i) ? new float[]{16 - THICK, 16} : new float[]{0, THICK};
    }

    /** A vertical face in side i's plane `depth` in, facing outward (toward i) or inward, spanning a..b along the side. */
    private static Face side(int i, float depth, boolean outward, float a, float b, float y0, float y1, boolean glass, int fades, boolean cull) {
        int ax = axisOf(i), other = 2 - ax;
        float[] f = new float[3], t = new float[3];
        f[ax] = t[ax] = at(i, depth);
        f[other] = Math.min(a, b);
        t[other] = Math.max(a, b);
        f[1] = y0;
        t[1] = y1;
        return new Face(outward ? SIDE_FACE[i] : SIDE_FACE[opposite(i)], f, t, glass, fades, cull);
    }

    /** A horizontal face at y over the box between side i's band and a..b along it (or any x/z rectangle). */
    private static Face flat(int dir, float y, float x0, float x1, float z0, float z1, boolean glass, int fades) {
        return new Face(dir, new float[]{Math.min(x0, x1), y, Math.min(z0, z1)}, new float[]{Math.max(x0, x1), y, Math.max(z0, z1)}, glass, fades, false);
    }

    /** A horizontal face over side i's band, a..b along the side. */
    private static Face bandFace(int dir, int i, float y, float a, float b, int fades) {
        float[] bd = band(i);
        return axisOf(i) == 2 ? flat(dir, y, a, b, bd[0], bd[1], true, fades) : flat(dir, y, bd[0], bd[1], a, b, true, fades);
    }

    /** The same face as liquid. */
    private static Face water(Face f) {
        return new Face(f.dir, f.from, f.to, false, 0, false);
    }

    /** An inner glass face with the liquid against it (same plane, facing out), or glass alone above the liquid. */
    private static void inner(List<Face> out, int i, float a, float b, float y0, float y1, float wetTo, int fades) {
        if (b == a || y1 <= y0) return;
        float split = Math.min(Math.max(wetTo, y0), y1);
        int up = 1 << F_UP, down = 1 << F_DOWN, lateral = fades & ~(up | down);
        if (split > y0) {
            out.add(side(i, THICK, false, a, b, y0, split, true, lateral | (fades & down) | (split >= y1 ? fades & up : 0), false));
            out.add(side(i, THICK, true, a, b, y0, split, false, 0, false));
        }
        if (y1 > split) out.add(side(i, THICK, false, a, b, split, y1, true, lateral | (fades & up) | (split <= y0 ? fades & down : 0), false));
    }

    /** All glass and liquid faces of a block with this shape. */
    public static List<Face> faces(int shape) {
        List<Face> out = new ArrayList<>();
        boolean up = has(shape, UP), down = has(shape, DOWN);
        float bottom = down ? 0 : BASE, wet = up ? 16 : LINE;
        int fadeTop = up ? 0 : 1 << F_UP, fadeFoot = down ? 0 : 1 << F_DOWN;
        boolean[] step = new boolean[4];
        for (int i = 0; i < 4; i++) step[i] = up && j(shape, i) && !has(shape, joinedAbove(i));

        for (int i = 0; i < 4; i++) {
            int[] es = ends(i);
            if (!j(shape, i)) {
                // ---- a wall
                float[] outer = new float[2], lo = new float[2], hi = new float[2], strip = new float[2];
                int fold = 0, foldHi = 0, taller = 0;
                boolean anyStep = false;
                for (int k = 0; k < 2; k++) {
                    int e = es[k];
                    boolean corner = !j(shape, e), inside = !corner && has(shape, diagonal(i, e));
                    outer[k] = at(e, 0);
                    lo[k] = corner ? at(e, THICK) : at(e, 0);
                    hi[k] = corner || step[e] ? at(e, THICK) : at(e, 0);
                    if (corner || inside) fold |= faceBit(e);
                    if (corner || inside || step[e]) foldHi |= faceBit(e);
                    // a top block's wall above the water line meets a taller neighbour's step: a fold
                    if (!up && j(shape, e) && has(shape, joinedAbove(e))) taller |= faceBit(e);
                    anyStep |= step[e];
                    // the corner square of the top outline goes to the north/south wall
                    strip[k] = corner && (i == E || i == W) ? at(e, THICK) : at(e, 0);
                }
                // outer face, on the block's face
                if (fadeTop != 0 && fadeFoot != 0) {
                    out.add(side(i, 0, true, outer[0], outer[1], bottom, LINE, true, fold | fadeFoot, true));
                    out.add(side(i, 0, true, outer[0], outer[1], LINE, 16, true, fold | fadeTop, true));
                } else {
                    out.add(side(i, 0, true, outer[0], outer[1], bottom, 16, true, fold | fadeTop | fadeFoot, true));
                }
                // inner face and the liquid against it
                if (up && anyStep) {
                    inner(out, i, lo[0], lo[1], bottom, LINE, wet, fold | fadeFoot);
                    inner(out, i, hi[0], hi[1], LINE, 16, wet, foldHi);
                } else if (!up) {
                    inner(out, i, lo[0], lo[1], bottom, LINE, wet, fold | fadeFoot);
                    inner(out, i, lo[0], lo[1], LINE, 16, wet, fold | taller | fadeTop);
                } else {
                    inner(out, i, lo[0], lo[1], bottom, 16, wet, fold | fadeFoot | fadeTop);
                }
                // the outline on top
                if (!up) out.add(bandFace(F_UP, i, 16, strip[0], strip[1], ALL));
            } else if (step[i]) {
                // ---- a step: a slab on side i from the neighbour's water line up
                float[] outer = new float[2], in = new float[2], under = new float[2];
                int foldOut = 0, foldIn = 0;
                for (int k = 0; k < 2; k++) {
                    int e = es[k];
                    boolean wall = !j(shape, e), d = has(shape, diagonal(i, e)), dUp = has(shape, diagonalAbove(i, e));
                    if (wall) {
                        outer[k] = at(e, THICK); in[k] = at(e, THICK); under[k] = at(e, THICK);
                        foldOut |= faceBit(e); foldIn |= faceBit(e);
                    } else if (step[e]) {
                        outer[k] = d ? at(e, 0) : at(e, THICK);
                        in[k] = at(e, THICK);
                        // the corner square's underside goes to the north/south step (if open water is under it)
                        under[k] = d && (i == N || i == S) ? at(e, 0) : at(e, THICK);
                        foldOut |= faceBit(e); foldIn |= faceBit(e);
                    } else {
                        // the neighbour beyond e goes on up too. Without the diagonal block, the short neighbour has
                        // a wall on e whose inner face the step's outer face meets; with it, the step carries on
                        outer[k] = d ? at(e, 0) : at(e, THICK);
                        in[k] = at(e, 0);
                        under[k] = d ? at(e, 0) : at(e, THICK);
                        if (!d || dUp) foldOut |= faceBit(e);
                        if (d && dUp) foldIn |= faceBit(e);
                    }
                }
                out.add(side(i, 0, true, outer[0], outer[1], LINE, 16, true, foldOut | 1 << F_DOWN, true));
                inner(out, i, in[0], in[1], LINE, 16, 16, foldIn | 1 << F_DOWN);
                out.add(bandFace(F_DOWN, i, LINE, under[0], under[1], ALL));
                out.add(water(bandFace(F_UP, i, LINE, under[0], under[1], 0))); // the water against its underside
            }
        }

        // ---- inside corners: 1 px pieces that carry the neighbours' inner faces round the corner
        int[][] corners = {{N, E}, {N, W}, {S, E}, {S, W}};
        for (int[] c : corners) {
            int a = c[0], b = c[1];
            if (!j(shape, a) || !j(shape, b) || has(shape, diagonal(a, b))) continue;
            for (int[] pair : new int[][]{{a, b}, {b, a}}) {
                int w = pair[0], t = pair[1];
                float[] span = band(t);
                // above the water line a step on either side covers this piece
                float top = up && (step[w] || step[t]) ? LINE : 16;
                inner(out, w, span[0], span[1], bottom, top, wet, faceBit(t) | faceBit(opposite(t)) | fadeFoot | (top == 16 ? fadeTop : 0));
            }
            float[] ba = band(a), bb = band(b);
            if (!up) out.add(flat(F_UP, 16, bb[0], bb[1], ba[0], ba[1], true, ALL)); // a is north/south, b east/west
        }

        // ---- pocket corners: the air over a short diagonal block reaches this block's corner above the line
        if (up) {
            for (int[] c : corners) {
                int a = c[0], b = c[1];
                if (!j(shape, a) || !j(shape, b) || !has(shape, diagonal(a, b)) || step[a] || step[b]
                        || has(shape, diagonalAbove(a, b))) continue;
                for (int[] pair : new int[][]{{a, b}, {b, a}}) {
                    float[] span = band(pair[1]);
                    inner(out, pair[0], span[0], span[1], LINE, 16, 16, faceBit(pair[1]) | faceBit(opposite(pair[1])) | 1 << F_DOWN);
                }
                float[] ba = band(a), bb = band(b);
                out.add(flat(F_DOWN, LINE, bb[0], bb[1], ba[0], ba[1], true, ALL));
                out.add(flat(F_UP, LINE, bb[0], bb[1], ba[0], ba[1], false, 0));
            }
        }

        // ---- the water's surface, inside the walls (inside corners' squares cut out)
        if (!up) {
            float[] xs = cuts(j(shape, W), j(shape, E)), zs = cuts(j(shape, N), j(shape, S));
            for (int zi = 0; zi + 1 < zs.length; zi++) {
                float rowLo = Float.NaN, rowHi = Float.NaN;
                for (int xi = 0; xi + 1 < xs.length; xi++) {
                    boolean keep = !cutOut(shape, xs[xi], xs[xi + 1], zs[zi], zs[zi + 1]);
                    if (keep && Float.isNaN(rowLo)) rowLo = xs[xi];
                    if (keep) rowHi = xs[xi + 1];
                    if ((!keep || xi + 2 == xs.length) && !Float.isNaN(rowLo)) {
                        out.add(flat(F_UP, LINE, rowLo, rowHi, zs[zi], zs[zi + 1], false, 0));
                        rowLo = rowHi = Float.NaN;
                    }
                }
            }
        }
        return out;
    }

    private static float[] cuts(boolean lowJoined, boolean highJoined) {
        List<Float> c = new ArrayList<>();
        c.add(lowJoined ? 0 : THICK);
        if (lowJoined) c.add(THICK);
        if (highJoined) c.add(16 - THICK);
        c.add(highJoined ? 16 : 16 - THICK);
        float[] out = new float[c.size()];
        for (int k = 0; k < out.length; k++) out[k] = c.get(k);
        return out;
    }

    /** A surface cell in an inside corner's square (the corner piece's glass is there). */
    private static boolean cutOut(int shape, float x0, float x1, float z0, float z1) {
        int[][] corners = {{N, E}, {N, W}, {S, E}, {S, W}};
        for (int[] c : corners) {
            if (!j(shape, c[0]) || !j(shape, c[1]) || has(shape, diagonal(c[0], c[1]))) continue;
            boolean east = c[1] == E, south = c[0] == S;
            boolean inX = east ? x0 >= 16 - THICK : x1 <= THICK;
            boolean inZ = south ? z0 >= 16 - THICK : z1 <= THICK;
            if (inX && inZ) return true;
        }
        return false;
    }
}
