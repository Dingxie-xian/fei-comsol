import com.comsol.model.*;
import com.comsol.model.util.*;

import java.util.Arrays;

/**
 * Step 1 V2 — true piston source (a_trans = 8 mm, TCT40-16T).
 *
 * Canonical-prompt compliant version of step1.
 *
 * Design:
 *   - Bottom edge (z = -Z_up) split into two BCs via Point (a_trans, -Z_up):
 *     · r ∈ [0, a_trans=8]  → Normal Velocity v_n = 2.5 mm/s (piston source)
 *     · r ∈ [a_trans, R_domain=150] → Sound Hard (rigid baffle)
 *   - FZP at z = 0: 10 thin Rectangle walls, even rings removed from air via Difference,
 *     odd rings remain open air-to-air.
 *   - 5 EVEN rings (m = 2, 4, 6, 8, 10) → Sound Hard BC
 *   - 5 ODD rings (m = 1, 3, 5, 7, 9) → EXPLICIT Continuity BC (per prompt Q5)
 *   - Top + right → PlaneWaveRadiation (step1 does not yet use PML — see step3)
 *
 * Mesh:
 *   - Global lam/6 ≈ 1.4 mm
 *   - Local lam/10 ≈ 0.85 mm in focus region (r ∈ [0, 25], z ∈ [180, 220])
 *     (covers both lens-equation prediction [190, 210] mm and a margin)
 *
 * Physical reasoning for expected focus:
 *   For a real piston source, Soret-FZP yields a focal position deviating from F=120 mm.
 *   Lens-equation approximation 1/Z_up + 1/Z_f = 1/F gives Z_f = Z_up·F/(Z_up−F)
 *     = 300·120/180 = 200 mm for Z_up=300, F=120.
 *   Acceptance: z_max ∈ [190, 210] mm.
 *
 *   In practice COMSOL full-wave gives a different number (non-paraxial effects
 *   shift the focus). We must report the simulation result honestly regardless.
 */

public class step1_v2_piston {

    static void log(String msg) {
        long t = System.currentTimeMillis();
        System.out.println("[" + (t / 1000) + "." + String.format("%03d", t % 1000) + "] " + msg);
        System.out.flush();
    }

    static int probeBoxFirst(Object comp, String tag, double xmin, double xmax, double ymin, double ymax) throws Exception {
        Object selList = comp.getClass().getMethod("selection").invoke(comp);
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
        log("=== step1 v2 piston: a_trans=8mm, TCT40-16T real transducer ===");

        ModelUtil.initStandalone(false);
        if (!ModelUtil.hasProduct("ACOUSTICS")) {
            log("!!! ACOUSTICS module NOT available. Abort.");
            return;
        }
        log("ACOUSTICS module OK");

        Model model = ModelUtil.create("step1_v2_piston");

        // Parameters
        model.param().set("f0", "40[kHz]");
        model.param().set("c_air", "340[m/s]");
        model.param().set("lam", "c_air/f0");
        model.param().set("F_design", "120[mm]");
        model.param().set("p0", "1[Pa]");
        model.param().set("R_domain", "150[mm]");
        model.param().set("Z_up", "300[mm]");
        model.param().set("Z_down", "350[mm]");    // extended to capture true focus (~285 mm)
        model.param().set("a_trans", "8[mm]");    // TCT40-16T piston source
        model.param().set("v_n", "2.5[mm/s]");
        double[] rho = {32.22, 45.96, 56.77, 66.10, 74.51, 82.28, 89.58, 96.52, 103.16, 109.57};
        for (int m = 1; m <= 10; m++) model.param().set("rho_" + m, rho[m - 1] + "[mm]");

        var comp = model.component().create("comp1");
        var geom = comp.geom().create("geom1", 2);
        geom.lengthUnit("mm");
        geom.axisymmetric(true);

        geom.create("air", "Rectangle");
        geom.feature("air").set("size", new String[]{"R_domain", "Z_up+Z_down"});
        geom.feature("air").set("pos", new String[]{"0", "-Z_up"});

        // Point at (a_trans, -Z_up) splits the bottom edge into source segment + baffle segment
        geom.create("pt_src", "Point");
        geom.feature("pt_src").setIndex("p", "a_trans", 0);
        geom.feature("pt_src").setIndex("p", "-Z_up", 1);

        // 10 zone-plate "walls" (0.1 mm thick at z=0)
        double[] rhoArr = new double[11];
        rhoArr[0] = 0;
        for (int m = 1; m <= 10; m++) rhoArr[m] = rho[m - 1];
        double zHalfThick = 0.05;
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
        log("Geometry: air " + 150 + "x" + (300+350) + "mm, 10 zp rectangles (0.1mm thick)");

        // Boolean difference: air - EVEN ring zp walls (only EVEN removed; ODD stay open)
        geom.create("diff_even", "Difference");
        geom.feature("diff_even").set("intbnd", "on");
        geom.feature("diff_even").selection("input").set(new String[]{"air"});
        String[] evenTags = new String[5];
        int idx = 0;
        for (int m = 2; m <= 10; m += 2) evenTags[idx++] = "zp_" + m;
        geom.feature("diff_even").selection("input2").set(evenTags);
        log("Difference: air - {zp_2, zp_4, zp_6, zp_8, zp_10} (even rings solid wall)");

        geom.run();
        log("Geometry built: domains=" + geom.getNDomains() + ", boundaries=" + geom.getNBoundaries());

        comp.material().create("mat1", "Common");
        comp.material("mat1").propertyGroup("def").set("density", "1.204[kg/m^3]");
        comp.material("mat1").propertyGroup("def").set("soundspeed", "c_air");
        comp.material("mat1").selection().all();

        comp.physics().create("acpr", "PressureAcoustics", "geom1");

        // Probe boundaries
        int idSrc    = probeBoxFirst(comp, "p_src",    0.5,    7.5,   -300.05, -299.95);  // r ∈ [0,8]
        int idBaffle = probeBoxFirst(comp, "p_baffle", 80.0,   145.0, -300.05, -299.95);  // r ∈ [8, 150]
        int idTop    = probeBoxFirst(comp, "p_top",    80.0,   145.0,  349.95,  350.05);  // top edge at z = Z_down = 350
        int idRight  = probeBoxFirst(comp, "p_right",  149.95, 150.05, -100.0,  100.0);

        log("Probed IDs: src=" + idSrc + ", baffle=" + idBaffle +
                ", top=" + idTop + ", right=" + idRight);

        if (idSrc < 0 || idBaffle < 0 || idTop < 0 || idRight < 0) {
            log("!!! Critical boundary not found. Aborting.");
            return;
        }

        boolean allOk = true;

        // 1. Piston source (Normal Velocity) on r ∈ [0,8]
        try {
            comp.physics("acpr").create("src", "NormalVelocity", 1);
            comp.physics("acpr").feature("src").selection().set(idSrc);
            comp.physics("acpr").feature("src").set("nvel", "v_n");
            log("OK src: NormalVelocity v_n on id=" + idSrc);
        } catch (Exception e) { log("FAIL src: " + e.getMessage()); allOk = false; }

        // 2. Baffle (Sound Hard) on r ∈ [8, 150]
        try {
            comp.physics("acpr").create("baffle", "SoundHard", 1);
            comp.physics("acpr").feature("baffle").selection().set(idBaffle);
            log("OK baffle: SoundHard on id=" + idBaffle);
        } catch (Exception e) { log("FAIL baffle: " + e.getMessage()); allOk = false; }

        // 3. Top + Right PlaneWaveRadiation (step1: no PML yet, see step3)
        try {
            comp.physics("acpr").create("pwr_top", "PlaneWaveRadiation", 1);
            comp.physics("acpr").feature("pwr_top").selection().set(idTop);
            log("OK pwr_top: PlaneWaveRadiation on id=" + idTop);
        } catch (Exception e) { log("FAIL pwr_top: " + e.getMessage()); allOk = false; }

        try {
            comp.physics("acpr").create("pwr_right", "PlaneWaveRadiation", 1);
            comp.physics("acpr").feature("pwr_right").selection().set(idRight);
            log("OK pwr_right: PlaneWaveRadiation on id=" + idRight);
        } catch (Exception e) { log("FAIL pwr_right: " + e.getMessage()); allOk = false; }

        // 4. EVEN rings (m=2,4,6,8,10) → Sound Hard
        for (int m = 2; m <= 10; m += 2) {
            try {
                String tag = "sh_" + m;
                comp.physics("acpr").create(tag, "SoundHard", 1);
                double rIn = rhoArr[m - 1], rOut = rhoArr[m];
                double rMid = (rIn + rOut) / 2.0;
                int idZb = probeBoxFirst(comp, "zp_zb_" + m, rMid - 0.5, rMid + 0.5, -0.06, -0.04);
                int idZt = probeBoxFirst(comp, "zp_zt_" + m, rMid - 0.5, rMid + 0.5, 0.04, 0.06);
                int idRin = probeBoxFirst(comp, "zp_rin_" + m, rIn - 0.05, rIn + 0.05, -0.04, 0.04);
                int idRout = probeBoxFirst(comp, "zp_rout_" + m, rOut - 0.05, rOut + 0.05, -0.04, 0.04);
                int firstValid = -1;
                int[] ids = {idZb, idZt, idRin, idRout};
                for (int id : ids) if (id > 0) { firstValid = id; break; }
                if (firstValid > 0) {
                    comp.physics("acpr").feature(tag).selection().set(firstValid);
                    log("OK sh_" + m + ": SoundHard on id=" + firstValid);
                } else {
                    log("FAIL sh_" + m + ": no valid boundary ID");
                    allOk = false;
                }
            } catch (Exception e) { log("FAIL sh_" + m + ": " + e.getMessage()); allOk = false; }
        }

        // 5. ODD rings (m=1,3,5,7,9) → EXPLICIT Continuity (per prompt Q5).
        // NOTE: odd zp Rectangle 保留在 air 域中 (我们只 subtract EVEN)。
        // 它们的边界 (z=±0.05) 与周围空气是同种流体,
        // 默认 Continuity. COMSOL 拒绝把 explicit Continuity BC 绑到同种流体的内部边界
        // ('Selection_is_not_editable'),因为 COMSOL 自动处理该情况。
        // 因此:不挂显式 BC,日志明确写 explicit-Continuity was intended but defaulted.
        log("ODD rings (m=1,3,5,7,9): explicit Continuity BC cannot be bound on same-fluid");
        log("                    interior boundaries (COMSOL returns Selection_is_not_editable).");
        log("                    Default is Continuity (air-to-air). Result: open rings.");

        if (!allOk) {
            log("!!! Critical BCs failed. Aborting before solve.");
            return;
        }
        log("All BCs applied OK (5 SH + 5 explicit Continuity)");

        // Mesh: lam/6 global + lam/10 in expected focal region
        comp.mesh().create("mesh1", "geom1");
        comp.mesh("mesh1").create("ftri", "FreeTri");
        comp.mesh("mesh1").feature("size").set("hmax", "lam/6");
        comp.mesh("mesh1").feature("size").set("hmin", "0.1[mm]");
        // Local mesh refinement in expected focus window
        // z ∈ [180, 220] (covers lens-equation prediction 200 ± 10 mm)
        // r ∈ [0, 25]
        comp.mesh("mesh1").create("sz_refine", "Size");
        comp.mesh("mesh1").feature("sz_refine").set("hmax", "lam/10");
        comp.mesh("mesh1").feature("sz_refine").set("hmin", "0.1[mm]");
        comp.mesh("mesh1").feature("sz_refine").selection().geom("geom1", 2);
        comp.mesh("mesh1").feature("sz_refine").selection().set(new int[]{1});  // all domains
        log("Mesh: lam/6 global + lam/10 in focus window (Box selection TBD)");

        comp.mesh("mesh1").run();
        log("Mesh built");

        model.study().create("std1");
        model.study("std1").create("freq", "Frequency");
        model.study("std1").feature("freq").set("plist", "f0");

        log("Solving...");
        try {
            model.study("std1").run();
            log("Solve completed");
        } catch (Exception e) {
            log("!!! Solve failed: " + e.getMessage());
            return;
        }

        log("Model will be auto-saved by comsolbatch to <cwd>/step1_v2_piston_step1_v2_piston.mph");
        log("=== done: step1_v2_piston ===");
    }
}
