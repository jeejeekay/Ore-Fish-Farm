package IGoByJake.orefishfarm.block;

import java.util.ArrayList;
import java.util.List;

/**
 * Every glass and liquid face of one aquarium block, worked out from which neighbours are in the same aquarium.
 * Plain Java with no Minecraft types, so it can be tested on its own (tools/TankGeometryCheck.java); the client's
 * TankExtrasModel bakes what this returns. The server works out each block's shape bits ({@link #shape}).
 *
 * <h2>Why it looks like this</h2>
 * Glass and liquid are both see-through, and Minecraft sorts see-through faces back to front by each face's centre.
 * That only works reliably when every glass face has a liquid face of exactly the same size right behind it (then
 * the glass's centre is always nearer). So below the water line every glass face has a <b>twin</b> 0.1 px further in.
 * Above the water line the glass has nothing behind it. The water's surface has no glass over it.
 *
 * <h2>Layout, in pixels of the block</h2>
 * <ul>
 *   <li>Base: 0..3 (drawn by the block model, solid) when there is no aquarium block below.</li>
 *   <li>Walls: on each side with no aquarium block beyond, glass at {@link #GLASS} in from the block edge, from the
 *       base (or 0) to 16. A wall's end goes to the block edge where the aquarium carries on that way (into the
 *       next block's wall, or round an inside corner), and stops at {@link #GLASS} at an outside corner, where it
 *       meets the side wall.</li>
 *   <li>Water line: {@link #LINE} in a block with nothing above it; the liquid fills to 16 in a block with one.</li>
 *   <li>Inside corners (aquarium on two sides but not diagonally between them): two 0.4 px slivers of glass that
 *       finish both walls into the corner.</li>
 *   <li>Steps (an aquarium block above this one, but not above the neighbour on that side): glass from the
 *       neighbour's water line up to 16 on that side, and a strip of water surface outside it so the neighbour's
 *       surface reaches the glass.</li>
 * </ul>
 * The glass fades in (stays see-through, never solid) wherever its surface folds: outside and inside corners, the top
 * of the aquarium and where it stands on the base. Everywhere it carries straight on it is plain, so a big aquarium
 * reads as one sheet of glass.
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

    public static final float GLASS = 0.4F, TWIN = 0.1F, LIQUID = GLASS + TWIN, BASE = 3, LINE = 13;

    /** Face directions, as Minecraft's Direction ordinals: down, up, north, south, west, east. */
    public static final int F_DOWN = 0, F_UP = 1, F_NORTH = 2, F_SOUTH = 3, F_WEST = 4, F_EAST = 5;
    private static final int[] SIDE_FACE = {F_NORTH, F_EAST, F_SOUTH, F_WEST};

    /** One face: a flat box (from == to on the face's axis), what it is, and for glass which edges fade. */
    public static final class Face {
        public final int dir;
        public final float[] from, to;
        public final boolean glass;
        /** Glass only: fading edges, as directions (Minecraft Direction ordinals) the edge faces. */
        public final int fades;

        Face(int dir, float[] from, float[] to, boolean glass, int fades) {
            this.dir = dir;
            this.from = from;
            this.to = to;
            this.glass = glass;
            this.fades = fades;
        }

        @Override
        public String toString() {
            return (glass ? "glass " : "liquid ") + dir + " " + java.util.Arrays.toString(from) + "->" + java.util.Arrays.toString(to);
        }
    }

    private static boolean has(int shape, int bit) { return (shape & bit) != 0; }

    private static boolean j(int shape, int i) { return has(shape, joined(i)); }

    /** The two sides beside side i (its ends): the lower-coordinate one first. */
    private static int[] ends(int i) {
        return (i == N || i == S) ? new int[]{W, E} : new int[]{N, S};
    }

    /** Horizontal axis index (0 = x, 2 = z) a side faces along, and whether it is the + end. */
    private static int axisOf(int i) { return (i == E || i == W) ? 0 : 2; }
    private static boolean plusSide(int i) { return i == E || i == S; }

    private static int faceBit(int side) { return 1 << SIDE_FACE[side]; }

    /** The wall plane of side i, `in` pixels in from that side's block edge. */
    private static float plane(int i, float in) { return plusSide(i) ? 16 - in : in; }

    /** Where a wall/step on side i ends toward side e: at the edge (carry on) or `in` from it (corner). */
    private static float endAt(int e, boolean corner, float in) {
        boolean lowEnd = !plusSide(e);
        if (corner) return lowEnd ? in : 16 - in;
        return lowEnd ? 0 : 16;
    }

    /** A vertical face on side i at `in` from its edge, spanning lo..hi along the side and y0..y1. */
    private static Face wallFace(int i, float in, float lo, float hi, float y0, float y1, boolean glass, int fades) {
        int ax = axisOf(i), other = 2 - ax;
        float[] f = new float[3], t = new float[3];
        f[ax] = t[ax] = plane(i, in);
        f[other] = lo;
        t[other] = hi;
        f[1] = y0;
        t[1] = y1;
        return new Face(SIDE_FACE[i], f, t, glass, fades);
    }

    /** A glass face on side i, `in` from its edge, and (if {@code twin}) its liquid twin 0.1 px further in. */
    private static void pane(List<Face> out, int i, float lo, float hi, float y0, float y1, boolean twin, int fades) {
        if (hi <= lo || y1 <= y0) return;
        if (twin) out.add(wallFace(i, LIQUID, lo, hi, y0, y1, false, 0));
        out.add(wallFace(i, GLASS, lo, hi, y0, y1, true, fades));
    }

    /** As {@link #pane}, split at the water line: twinned below {@code liquidTop}, glass alone above it. */
    private static void glassAndTwin(List<Face> out, int i, float lo, float hi, float y0, float y1, float liquidTop,
                                     int sideFades, boolean fadeBottom, boolean fadeTop) {
        float split = Math.min(Math.max(liquidTop, y0), y1);
        if (split > y0) pane(out, i, lo, hi, y0, split, true,
                sideFades | (fadeBottom ? 1 << F_DOWN : 0) | (fadeTop && split >= y1 ? 1 << F_UP : 0));
        if (y1 > split) pane(out, i, lo, hi, split, y1, false,
                sideFades | (fadeTop ? 1 << F_UP : 0) | (fadeBottom && split <= y0 ? 1 << F_DOWN : 0));
    }

    /** The edge of side e, `in` from it: where things on another side stop short of e. */
    private static float near(int e, float in) {
        return plusSide(e) ? 16 - in : in;
    }

    /** The band along side i between its block edge and its step glass. */
    private static float[] band(int i) {
        return plusSide(i) ? new float[]{16 - GLASS, 16} : new float[]{0, GLASS};
    }

    /** All glass and liquid faces of a block with this shape. */
    public static List<Face> faces(int shape) {
        List<Face> out = new ArrayList<>();
        boolean up = has(shape, UP), down = has(shape, DOWN);
        float bottom = down ? 0 : BASE, liquidTop = up ? 16 : LINE;
        boolean[] step = new boolean[4]; // a step on side i: the neighbour there is a top block, this one goes on up
        for (int i = 0; i < 4; i++) step[i] = up && j(shape, i) && !has(shape, joinedAbove(i));

        for (int i = 0; i < 4; i++) {
            int[] es = ends(i);
            if (!j(shape, i)) {
                // ---- a wall: below the water line (or all the way, in a block with water above), its ends turn at
                // outside corners, carry on into the next block, or carry on round an inside corner
                float[] lim = new float[2], inner = new float[2];
                int fades = 0, upperFades = 0;
                boolean anyBand = false;
                for (int k = 0; k < 2; k++) {
                    int e = es[k];
                    boolean corner = !j(shape, e), inside = !corner && has(shape, diagonal(i, e));
                    lim[k] = endAt(e, corner, GLASS);
                    if (corner || inside) fades |= faceBit(e);
                    // above the water line, next to a step on e, the wall's water ends at the step glass; the band
                    // outside the step is the neighbour's air, glass on its own
                    inner[k] = step[e] ? near(e, GLASS) : lim[k];
                    anyBand |= step[e];
                    if (corner || (inside && !(j(shape, e) && has(shape, joinedAbove(e)) && !up))) upperFades |= faceBit(e);
                }
                if (!up) {
                    pane(out, i, lim[0], lim[1], bottom, LINE, true, fades | (down ? 0 : 1 << F_DOWN));
                    pane(out, i, lim[0], lim[1], LINE, 16, false, upperFades | 1 << F_UP);
                } else if (!anyBand) {
                    pane(out, i, lim[0], lim[1], bottom, 16, true, fades | (down ? 0 : 1 << F_DOWN));
                } else {
                    pane(out, i, lim[0], lim[1], bottom, LINE, true, fades | (down ? 0 : 1 << F_DOWN));
                    int innerFades = 0;
                    for (int k = 0; k < 2; k++) if (!step[es[k]] && (fades & faceBit(es[k])) != 0) innerFades |= faceBit(es[k]);
                    pane(out, i, inner[0], inner[1], LINE, 16, true, innerFades);
                    for (int k = 0; k < 2; k++) {
                        int e = es[k];
                        if (!step[e]) continue;
                        float a = Math.min(inner[k], lim[k]), b = Math.max(inner[k], lim[k]);
                        int f = 1 << F_UP | (has(shape, diagonal(i, e)) ? faceBit(e) : 0);
                        pane(out, i, a, b, LINE, 16, false, f);
                    }
                }
            } else if (step[i]) {
                // ---- a step: glass from the neighbour's water line to 16, with the neighbour's surface run up to it
                float[] lim = new float[2], strip = new float[2];
                int fades = 0;
                for (int k = 0; k < 2; k++) {
                    int e = es[k];
                    boolean wall = !j(shape, e), diag = has(shape, diagonal(i, e));
                    if (wall) {
                        lim[k] = endAt(e, true, GLASS);
                        fades |= faceBit(e);
                        strip[k] = endAt(e, true, LIQUID);
                    } else if (step[e]) {
                        lim[k] = endAt(e, true, GLASS);
                        fades |= faceBit(e);
                        // two steps meet: the corner square is open water if the aquarium is there; otherwise the
                        // short neighbour's side wall carries on across this block's band to the corner
                        // (without the aquarium there it's this block's inside corner, whose slivers close it)
                        boolean takeCorner = (i == N || i == S) && diag;
                        strip[k] = endAt(e, !takeCorner, diag ? GLASS : LIQUID);
                    } else if (diag) {
                        lim[k] = endAt(e, false, 0);
                        strip[k] = endAt(e, false, 0);
                    } else {
                        // the neighbour beyond e goes on up but the one diagonally doesn't: an inside corner of this
                        // block, whose slivers carry the step on round it
                        lim[k] = endAt(e, true, GLASS);
                        strip[k] = endAt(e, true, LIQUID);
                    }
                }
                pane(out, i, lim[0], lim[1], LINE, 16, true, fades);
                int ax = axisOf(i), other = 2 - ax;
                float[] f = new float[3], t = new float[3], bnd = band(i);
                f[1] = t[1] = LINE;
                f[ax] = bnd[0];
                t[ax] = bnd[1];
                f[other] = strip[0];
                t[other] = strip[1];
                if (t[other] > f[other]) out.add(new Face(F_UP, f, t, false, 0));
            }
        }

        // ---- inside corners: slivers that finish both walls into the corner
        int[][] corners = {{N, E}, {N, W}, {S, E}, {S, W}};
        for (int[] c : corners) {
            int a = c[0], b = c[1];
            if (!j(shape, a) || !j(shape, b) || has(shape, diagonal(a, b))) continue;
            for (int[] pair : new int[][]{{a, b}, {b, a}}) {
                int wallSide = pair[0], toward = pair[1];
                // a sliver facing `wallSide`, between `toward`'s block edge and the other sliver's plane
                float lo = plusSide(toward) ? 16 - GLASS : 0, hi = plusSide(toward) ? 16 : GLASS;
                int fades = faceBit(toward) | faceBit(opposite(toward)); // the whole sliver is the fold
                // the sliver lies in `toward`'s band: with a step there, above the water line it's air behind
                float wet = step[toward] ? LINE : liquidTop;
                glassAndTwin(out, wallSide, lo, hi, bottom, 16, wet, fades, !down, !up);
            }
        }

        // ---- a pocket corner: this block and both neighbours go on up, but the block diagonally between them is a
        // top block; the air over it reaches into this block's corner, and the steps round it meet here
        if (up) {
            for (int[] c : corners) {
                int a = c[0], b = c[1];
                if (!j(shape, a) || !j(shape, b) || !has(shape, diagonal(a, b)) || step[a] || step[b]
                        || has(shape, diagonalAbove(a, b))) continue;
                float[] ba = band(a), bb = band(b);
                for (int[] pair : new int[][]{{a, b}, {b, a}}) {
                    float[] span = band(pair[1]);
                    pane(out, pair[0], span[0], span[1], LINE, 16, true, faceBit(pair[1]) | faceBit(opposite(pair[1])));
                }
                int ax = axisOf(a), bx = axisOf(b);
                float[] f = new float[3], t = new float[3];
                f[1] = t[1] = LINE;
                f[ax] = ba[0]; t[ax] = ba[1];
                f[bx] = bb[0]; t[bx] = bb[1];
                out.add(new Face(F_UP, f, t, false, 0));
            }
        }

        // ---- the water's surface, inside the glass (with inside corners cut out)
        if (!up) {
            float[] xs = cuts(j(shape, W), j(shape, E)), zs = cuts(j(shape, N), j(shape, S));
            for (int zi = 0; zi + 1 < zs.length; zi++) {
                float rowLo = Float.NaN, rowHi = Float.NaN;
                for (int xi = 0; xi + 1 < xs.length; xi++) {
                    boolean keep = !cutOut(shape, xs[xi], xs[xi + 1], zs[zi], zs[zi + 1]);
                    if (keep && Float.isNaN(rowLo)) rowLo = xs[xi];
                    if (keep) rowHi = xs[xi + 1];
                    if ((!keep || xi + 2 == xs.length) && !Float.isNaN(rowLo)) {
                        out.add(new Face(F_UP, new float[]{rowLo, LINE, zs[zi]}, new float[]{rowHi, LINE, zs[zi + 1]}, false, 0));
                        rowLo = rowHi = Float.NaN;
                    }
                }
            }
        }
        return out;
    }

    private static int opposite(int i) { return (i + 2) % 4; }

    /** Cut lines across the surface along one axis: the glass on unjoined sides, the inside-corner squares. */
    private static float[] cuts(boolean lowJoined, boolean highJoined) {
        float lo = lowJoined ? 0 : LIQUID, hi = highJoined ? 16 : 16 - LIQUID;
        List<Float> c = new ArrayList<>();
        c.add(lo);
        if (lowJoined) c.add(LIQUID);
        if (highJoined) c.add(16 - LIQUID);
        c.add(hi);
        float[] out = new float[c.size()];
        for (int k = 0; k < out.length; k++) out[k] = c.get(k);
        return out;
    }

    /** A surface cell in an inside corner's square (outside the glass there). */
    private static boolean cutOut(int shape, float x0, float x1, float z0, float z1) {
        int[][] corners = {{N, E}, {N, W}, {S, E}, {S, W}};
        for (int[] c : corners) {
            if (!j(shape, c[0]) || !j(shape, c[1]) || has(shape, diagonal(c[0], c[1]))) continue;
            boolean east = c[1] == E, south = c[0] == S;
            boolean inX = east ? x0 >= 16 - LIQUID : x1 <= LIQUID;
            boolean inZ = south ? z0 >= 16 - LIQUID : z1 <= LIQUID;
            if (inX && inZ) return true;
        }
        return false;
    }
}
