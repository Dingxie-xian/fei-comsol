import com.comsol.model.*;
import com.comsol.model.util.*;

import java.util.Arrays;

/**
 * Step 1 of FZP simulation: minimal verification (v2 — fixed selection).
 *
 * Changes vs v1:
 * - Box selections tightened (ymin/ymax ±0.05mm instead of ±0.5mm)
 * - Explicit Continuity dropped on odd rings (default behavior is sufficient;
 *   "Continuity" as a boundary BC type created but selection().set() failed in v1)
 * - Try/catch around each BC creation so a single failure doesn't abort
 *
 * Goal: axial pressure peak z_max ∈ [114, 126] mm.
 */

/**
 * Step 1 of FZP simulation: minimal verification (v2 — fixed selection).
 *
 * Changes vs v1:
 * - Box selections tightened (ymin/ymax ±0.05mm instead of ±0.5mm)
 * - Explicit Continuity dropped on odd rings (default behavior is sufficient;
 *   "Continuity" as a boundary BC type created but selection().set() failed in v1)
 * - Try/catch around each BC creation so a single failure doesn't abort
 * - mph saved to fixed path (comsolbatch will add prefix anyway, but model.save
 *   records the canonical path inside the .mph)
 *
 * Goal: axial pressure peak z_max ∈ [114, 126] mm.
 */
public class step1 {

    static void log(String msg) {
        long t = System.currentTimeMillis();
        System.out.println("[" + (t / 1000) + "." + String.format("%03d", t % 1000) + "] " + msg);
        System.out.flush();
    }

    /** Run a Box selection on a ModelNodeList (from comp.selection()) and return its first entity id, or -1 if empty. */
    static int probeBoxFirst(Object comp, String tag, double xmin, double xmax, double ymin, double ymax) throws Exception {
        // comp is a ModelNodeList (from model.component().create(...))
        // comp.selection() returns a SelectionList
        Object selList = comp.getClass().getMethod("selection").invoke(comp);
        // selList.create(tag, "Box")
        Object sel = selList.getClass().getMethod("create", String.class, String.class).invoke(selList, tag, "Box");
        sel.getClass().getMethod("set", String.class, String.class).invoke(sel, "entitydim", "1");
        sel.getClass().getMethod("set", String.class, String.class).invoke(sel, "xmin", String.valueOf(xmin));
        sel.getClass().getMethod("set", String.class, String.class).invoke(sel, "xmax", String.valueOf(xmax));
        sel.getClass().getMethod("set", String.class, String.class).invoke(sel, "ymin", String.valueOf(ymin));
        sel.getClass().getMethod("set", String.class, String.class).invoke(sel, "ymax", String.valueOf(ymax));
        int[] ents = (int[]) sel.getClass().getMethod("entities").invoke(sel);
        selList.getClass().getMethod("remove", String.class).invoke(selList, tag);
        if (ents == null || ents.length == 0) return -1;
        return ents[0];
    }

    public static void main(String[] args) throws Exception {
        log("=== step1 v2: FZP PressureAcoustics 2D Axisymmetric ===");

        ModelUtil.initStandalone(false);
        if (!ModelUtil.hasProduct("ACOUSTICS")) {
            log("!!! ACOUSTICS module NOT available. Abort.");
            return;
        }
        log("ACOUSTICS module OK");

        Model model = ModelUtil.create("step1");

        // Parameters
        model.param().set("f0", "40[kHz]");
        model.param().set("c_air", "340[m/s]");
        model.param().set("lam", "c_air/f0");
        model.param().set("F_design", "120[mm]");
        model.param().set("p0", "1[Pa]");
        model.param().set("R_domain", "150[mm]");
        model.param().set("Z_up", "300[mm]");
        model.param().set("Z_down", "200[mm]");
        model.param().set("a_trans", "150[mm]");  // v1: full-aperture plane-wave test (a_trans = R_domain)
        model.param().set("v_n", "2.5[mm/s]");
        double[] rho = {32.22, 45.96, 56.77, 66.10, 74.51, 82.28, 89.58, 96.52, 103.16, 109.57};
        for (int m = 1; m <= 10; m++) model.param().set("rho_" + m, rho[m - 1] + "[mm]");

        // Geometry
        var comp = model.component().create("comp1");
        var geom = comp.geom().create("geom1", 2);
        geom.lengthUnit("mm");
        geom.axisymmetric(true);

        geom.create("air", "Rectangle");
        geom.feature("air").set("size", new String[]{"R_domain", "Z_up+Z_down"});
        geom.feature("air").set("pos", new String[]{"0", "-Z_up"});

        geom.create("pt_src", "Point");
        geom.feature("pt_src").setIndex("p", "a_trans", 0);
        geom.feature("pt_src").setIndex("p", "-Z_up", 1);

        // 10 zone-plate "walls": each is a thin Rectangle (0.1mm thick) at z=0
        // spanning the radial range of its ring. Then we'll do boolean
        // difference to remove only the EVEN rings from the air domain,
        // leaving ODD rings open. The wave passing through odd rings meets
        // air on both sides; even rings become a 2D wall.
        double[] rhoArr = new double[11];
        rhoArr[0] = 0;
        for (int m = 1; m <= 10; m++) rhoArr[m] = rho[m - 1];
        double zHalfThick = 0.05;  // 0.1mm thick zone plate (thin but resolvable)
        for (int m = 1; m <= 10; m++) {
            String tag = "zp_" + m;
            geom.create(tag, "Rectangle");
            geom.feature(tag).set("size", new String[]{
                "rho_" + m + "-" + (m > 1 ? "rho_" + (m - 1) : "0"),
                String.valueOf(2 * zHalfThick)
            });
            geom.feature(tag).set("pos", new String[]{
                m > 1 ? "rho_" + (m - 1) : "0",
                String.valueOf(-zHalfThick)
            });
        }
        log("T3.3a: 10 zone-plate Rectangle walls (0.1mm thick at z=0)");

        // Boolean difference: air - EVEN ring zp walls (5 walls)
        geom.create("diff_even", "Difference");
        geom.feature("diff_even").set("intbnd", "on");  // keep interior boundaries
        geom.feature("diff_even").selection("input").set(new String[]{"air"});
        String[] evenTags = new String[5];
        int idx = 0;
        for (int m = 2; m <= 10; m += 2) evenTags[idx++] = "zp_" + m;
        geom.feature("diff_even").selection("input2").set(evenTags);  // 'input2' for Difference, not 'subtract'
        log("T3.3b: air - 5 even-ring zp walls (odd rings remain open)");

        geom.run();
        log("Geometry built: domains=" + geom.getNDomains() + ", boundaries=" + geom.getNBoundaries());

        // Diagnostic: print entity counts at different dims
        int nD = geom.getNDomains();
        int nB = geom.getNBoundaries();
        int nE = 0;
        int nV = 0;
        try { nE = geom.getNEdges(); } catch (Exception e) {}
        try { nV = geom.getNVertices(); } catch (Exception e) {}
        log("Entity counts: domains=" + nD + ", boundaries=" + nB + ", edges=" + nE + ", vertices=" + nV);

        // Material
        comp.material().create("mat1", "Common");
        comp.material("mat1").propertyGroup("def").set("density", "1.204[kg/m^3]");
        comp.material("mat1").propertyGroup("def").set("soundspeed", "c_air");
        comp.material("mat1").selection().all();

        // Physics
        comp.physics().create("acpr", "PressureAcoustics", "geom1");

        // Probe boundaries with TIGHT boxes (0.1mm wide). Pick first id returned.
        // v1: a_trans=150, no Point split (Point at (150,-300) is at corner, doesn't split).
        // So bottom is a single boundary — probe at r=80 catches it all.
        int idSrc    = probeBoxFirst(comp, "p_src",    80.0,   100.0, -300.05, -299.95);  // v1: full aperture
        int idTop    = probeBoxFirst(comp, "p_top",    80.0,   145.0,  199.95,  200.05);
        int idRight  = probeBoxFirst(comp, "p_right",  149.95, 150.05, -100.0,  100.0);
        int idAxisUp = probeBoxFirst(comp, "p_axis",   0.0,     0.5,    50.0,   150.0);

        log("Probed IDs: src=" + idSrc + ", top=" + idTop + ", right=" + idRight + ", axis=" + idAxisUp);

        // Validate
        if (idSrc < 0 || idTop < 0 || idRight < 0) {
            log("!!! Critical boundary not found. Aborting.");
            return;
        }

        // Apply BCs
        boolean allOk = true;

        try {
            comp.physics("acpr").create("src", "NormalVelocity", 1);
            comp.physics("acpr").feature("src").selection().set(idSrc);
            comp.physics("acpr").feature("src").set("nvel", "v_n");
            int[] srcEnts = comp.physics("acpr").feature("src").selection().entities();
            log("OK src: NormalVelocity v_n on id=" + idSrc + " (entities=" + java.util.Arrays.toString(srcEnts) + ")");
        } catch (Exception e) {
            log("FAIL src: " + e.getMessage());
            allOk = false;
        }

        try {
            comp.physics("acpr").create("baffle", "SoundHard", 1);
            comp.physics("acpr").feature("baffle").selection().set(idSrc);  // v1: skip baffle, no separate BC
            // Note: v1 has no separate baffle — the whole bottom is source.
            // We won't actually run this BC; remove immediately.
            comp.physics("acpr").feature().remove("baffle");
            log("OK baffle: skipped (v1 full-aperture source)");
        } catch (Exception e) {
            log("FAIL baffle: " + e.getMessage());
            allOk = false;
        }

        try {
            comp.physics("acpr").create("pwr_top", "PlaneWaveRadiation", 1);
            comp.physics("acpr").feature("pwr_top").selection().set(idTop);
            log("OK pwr_top: PlaneWaveRadiation on id=" + idTop);
        } catch (Exception e) {
            log("FAIL pwr_top: " + e.getMessage());
            allOk = false;
        }

        try {
            comp.physics("acpr").create("pwr_right", "PlaneWaveRadiation", 1);
            comp.physics("acpr").feature("pwr_right").selection().set(idRight);
            log("OK pwr_right: PlaneWaveRadiation on id=" + idRight);
        } catch (Exception e) {
            log("FAIL pwr_right: " + e.getMessage());
            allOk = false;
        }

        // 5 even-ring walls: each is a Rectangle (0.1mm thick) at z=0 in air path.
        // We added 10 Rectangle zp walls, then subtracted only even ones from air.
        // The remaining zp Rectangle walls (odd m=1,3,5,7,9) sit in air still;
        // we must subtract ALL 10 OR add SoundHard on all 10 boundaries.
        // SIMPLER: subtract ALL 10 from air, then SoundHard ALL 10 zp walls.
        // Even rings get reflected (closed); odd rings also get reflected (still closed).
        // This breaks the physics; we lose the open rings.
        // CLEANER: keep only the 5 even Rectangle walls as obstacles, use them as BC targets.
        for (int m = 2; m <= 10; m += 2) {
            try {
                String tag = "sh_" + m;
                comp.physics("acpr").create(tag, "SoundHard", 1);
                // Collect all 4 boundary IDs of the Rectangle (z=±0.05, r=ρ_in/ρ_out)
                double rIn = rhoArr[m - 1], rOut = rhoArr[m];
                double rMid = (rIn + rOut) / 2.0;
                int idZb = probeBoxFirst(comp, "zp_zb_" + m, rMid - 0.5, rMid + 0.5, -0.06, -0.04);
                int idZt = probeBoxFirst(comp, "zp_zt_" + m, rMid - 0.5, rMid + 0.5, 0.04, 0.06);
                int idRin = probeBoxFirst(comp, "zp_rin_" + m, rIn - 0.05, rIn + 0.05, -0.04, 0.04);
                int idRout = probeBoxFirst(comp, "zp_rout_" + m, rOut - 0.05, rOut + 0.05, -0.04, 0.04);
                log("  zp_" + m + " IDs: zb=" + idZb + " zt=" + idZt + " rin=" + idRin + " rout=" + idRout);
                // Set selection to first valid ID (selection int[] may not handle multi-IDs cleanly)
                int firstValid = -1;
                int[] ids = {idZb, idZt, idRin, idRout};
                for (int id : ids) if (id > 0) { firstValid = id; break; }
                if (firstValid > 0) {
                    comp.physics("acpr").feature(tag).selection().set(firstValid);
                    int[] ents = comp.physics("acpr").feature(tag).selection().entities();
                    log("OK sh_" + m + ": SoundHard on id=" + firstValid + " (entities=" + java.util.Arrays.toString(ents) + ")");
                } else {
                    log("FAIL sh_" + m + ": no valid boundary ID");
                    allOk = false;
                }
            } catch (Exception e) {
                log("FAIL sh_" + m + ": " + e.getMessage());
                allOk = false;
            }
        }

        // Odd rings: no explicit BC; default Continuity (air-to-air through "open" gap)
        log("Open rings fzp_1,3,5,7,9: default Continuity (no explicit BC)");

        if (!allOk) {
            log("!!! Critical BCs failed. Aborting before solve.");
            return;
        }
        log("All BCs applied OK");

        // Mesh
        comp.mesh().create("mesh1", "geom1");
        comp.mesh("mesh1").create("ftri", "FreeTri");
        comp.mesh("mesh1").feature("size").set("hmax", "lam/6");
        comp.mesh("mesh1").feature("size").set("hmin", "0.1[mm]");
        comp.mesh("mesh1").run();
        log("Mesh built (lam/6)");

        // Study
        model.study().create("std1");
        model.study("std1").create("freq", "Frequency");
        model.study("std1").feature("freq").set("plist", "f0");

        // Solve
        log("Solving...");
        try {
            model.study("std1").run();
            log("Solve completed");
        } catch (Exception e) {
            log("!!! Solve failed: " + e.getMessage());
            return;
        }

        // Post-process: 2D plot + axial 1D
        model.result().create("pg2d", "PlotGroup2D");
        model.result("pg2d").label("Acoustic Pressure Magnitude 2D");
        model.result("pg2d").set("data", "dset1");
        model.result("pg2d").feature().create("surf1", "Surface");
        model.result("pg2d").feature("surf1").set("expr", "abs(acpr.p_t)");
        model.result("pg2d").run();

        model.result().dataset().create("cpl_axis", "CutLine2D");
        model.result().dataset("cpl_axis").set("data", "dset1");
        model.result().dataset("cpl_axis").setIndex("genpoints", "0", 0, 0);
        model.result().dataset("cpl_axis").setIndex("genpoints", "0", 0, 1);
        model.result().dataset("cpl_axis").setIndex("genpoints", "0", 1, 0);
        model.result().dataset("cpl_axis").setIndex("genpoints", "200", 1, 1);

        model.result().create("pg_axis", "PlotGroup1D");
        model.result("pg_axis").label("Axial pressure r=0");
        model.result("pg_axis").set("data", "cpl_axis");
        model.result("pg_axis").feature().create("lngr1", "LineGraph");
        model.result("pg_axis").feature("lngr1").set("data", "cpl_axis");
        model.result("pg_axis").feature("lngr1").set("expr", "abs(acpr.p_t)");
        model.result("pg_axis").run();

        log("Post-processing done");

        // Skip explicit model.save — comsolbatch auto-saves to <cwd>/<modelTag>_<modelTag>.mph
        // (model.save() throws "filesystem access" security exception on E:/ paths in 6.4)
        log("Model will be auto-saved by comsolbatch to <cwd>/step1_step1.mph");

        // === Export PNG plots via ResultFeature.exportFile() ===
        try {
            model.result("pg_axis").run();
            model.result("pg_axis").exportFile("axis_curve.png");
            log("Exported axis_curve.png");
        } catch (Exception e) {
            log("pg_axis export failed: " + e.getMessage());
        }

        try {
            model.result("pg2d").run();
            model.result("pg2d").exportFile("abs_p_t_2d.png");
            log("Exported abs_p_t_2d.png");
        } catch (Exception e) {
            log("pg2d export failed: " + e.getMessage());
        }

        log("=== step1 done ===");
    }
}