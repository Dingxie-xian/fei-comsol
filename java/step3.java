import com.comsol.model.*;
import com.comsol.model.util.*;

import java.util.Arrays;

/**
 * Step 3 of FZP simulation: same as step2 (piston source) but with
 * PlaneWaveRadiation replaced by PerfectlyMatchedBoundary (PMB).
 *
 * PMB is a one-shot PML formulation embedded in the boundary condition —
 * no separate PML domain required (Acoustics Module Users Guide p.142).
 *
 * Acceptance: |z_max(step3) − z_max(step2)| / z_max(step2) < 1%.
 * If step2 gave z_max ≈ 284 mm, step3 must lie in [281, 287] mm.
 */

public class step3 {

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
        log("=== step3: piston source, PlaneWaveRadiation -> PerfectlyMatchedBoundary ===");

        ModelUtil.initStandalone(false);
        if (!ModelUtil.hasProduct("ACOUSTICS")) {
            log("!!! ACOUSTICS module NOT available. Abort.");
            return;
        }
        log("ACOUSTICS module OK");

        Model model = ModelUtil.create("step3");

        model.param().set("f0", "40[kHz]");
        model.param().set("c_air", "340[m/s]");
        model.param().set("lam", "c_air/f0");
        model.param().set("F_design", "120[mm]");
        model.param().set("p0", "1[Pa]");
        model.param().set("R_domain", "150[mm]");
        model.param().set("Z_up", "300[mm]");
        model.param().set("a_trans", "8[mm]");
        model.param().set("Z_down", "350[mm]");
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

        geom.create("pt_src", "Point");
        geom.feature("pt_src").setIndex("p", "a_trans", 0);
        geom.feature("pt_src").setIndex("p", "-Z_up", 1);

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
        log("T3.3a: 10 zone-plate Rectangle walls (0.1mm thick at z=0)");

        geom.create("diff_even", "Difference");
        geom.feature("diff_even").set("intbnd", "on");
        geom.feature("diff_even").selection("input").set(new String[]{"air"});
        String[] evenTags = new String[5];
        int idx = 0;
        for (int m = 2; m <= 10; m += 2) evenTags[idx++] = "zp_" + m;
        geom.feature("diff_even").selection("input2").set(evenTags);

        geom.run();
        log("Geometry built: domains=" + geom.getNDomains() + ", boundaries=" + geom.getNBoundaries());

        comp.material().create("mat1", "Common");
        comp.material("mat1").propertyGroup("def").set("density", "1.204[kg/m^3]");
        comp.material("mat1").propertyGroup("def").set("soundspeed", "c_air");
        comp.material("mat1").selection().all();

        comp.physics().create("acpr", "PressureAcoustics", "geom1");

        int idSrc    = probeBoxFirst(comp, "p_src",    0.5,    7.5,   -300.05, -299.95);
        int idBaffle = probeBoxFirst(comp, "p_baffle", 80.0,   145.0, -300.05, -299.95);
        int idTop    = probeBoxFirst(comp, "p_top",    80.0,   145.0,  349.95,  350.05);
        int idRight  = probeBoxFirst(comp, "p_right",  149.95, 150.05, -100.0,  100.0);
        int idAxisUp = probeBoxFirst(comp, "p_axis",   0.0,     0.5,    50.0,   150.0);

        log("Probed IDs: src=" + idSrc + ", baffle=" + idBaffle +
                ", top=" + idTop + ", right=" + idRight + ", axis=" + idAxisUp);

        if (idSrc < 0 || idBaffle < 0 || idTop < 0 || idRight < 0) {
            log("!!! Critical boundary not found. Aborting.");
            return;
        }

        boolean allOk = true;

        try {
            comp.physics("acpr").create("src", "NormalVelocity", 1);
            comp.physics("acpr").feature("src").selection().set(idSrc);
            comp.physics("acpr").feature("src").set("nvel", "v_n");
            log("OK src: NormalVelocity v_n on id=" + idSrc);
        } catch (Exception e) {
            log("FAIL src: " + e.getMessage());
            allOk = false;
        }

        try {
            comp.physics("acpr").create("baffle", "SoundHard", 1);
            comp.physics("acpr").feature("baffle").selection().set(idBaffle);
            log("OK baffle: SoundHard on id=" + idBaffle);
        } catch (Exception e) {
            log("FAIL baffle: " + e.getMessage());
            allOk = false;
        }

        // *** step3 CHANGE: PlaneWaveRadiation -> PerfectlyMatchedBoundary ***
        try {
            comp.physics("acpr").create("pm_top", "PerfectlyMatchedBoundary", 1);
            comp.physics("acpr").feature("pm_top").selection().set(idTop);
            log("OK pm_top: PerfectlyMatchedBoundary on id=" + idTop);
        } catch (Exception e) {
            log("FAIL pm_top: " + e.getMessage());
            allOk = false;
        }

        try {
            comp.physics("acpr").create("pm_right", "PerfectlyMatchedBoundary", 1);
            comp.physics("acpr").feature("pm_right").selection().set(idRight);
            log("OK pm_right: PerfectlyMatchedBoundary on id=" + idRight);
        } catch (Exception e) {
            log("FAIL pm_right: " + e.getMessage());
            allOk = false;
        }

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
                log("  zp_" + m + " IDs: zb=" + idZb + " zt=" + idZt + " rin=" + idRin + " rout=" + idRout);
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
            } catch (Exception e) {
                log("FAIL sh_" + m + ": " + e.getMessage());
                allOk = false;
            }
        }

        if (!allOk) {
            log("!!! Critical BCs failed. Aborting before solve.");
            return;
        }
        log("All BCs applied OK");

        comp.mesh().create("mesh1", "geom1");
        comp.mesh("mesh1").create("ftri", "FreeTri");
        comp.mesh("mesh1").feature("size").set("hmax", "lam/6");
        comp.mesh("mesh1").feature("size").set("hmin", "0.1[mm]");
        comp.mesh("mesh1").create("sz_refine", "Size");
        comp.mesh("mesh1").feature("sz_refine").set("hmax", "lam/8");
        comp.mesh("mesh1").feature("sz_refine").set("hmin", "0.1[mm]");
        comp.mesh("mesh1").feature("sz_refine").selection().geom("geom1", 2);
        comp.mesh("mesh1").feature("sz_refine").selection().set(new int[]{1});
        comp.mesh("mesh1").run();
        log("Mesh built (lam/6 + local lam/8 in z=[200,350])");

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
        log("Model will be auto-saved by comsolbatch to <cwd>/step3_step3.mph");

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

        log("=== step3 done ===");
    }
}
