import com.comsol.model.*;
import com.comsol.model.util.*;

import java.util.Arrays;

/**
 * Step 1 v2 piston PML — parameterized for mesh convergence + PML mapped test.
 *
 * Args:
 *   args[0] = meshLevel (denominator of lam, default 8). E.g. 6, 8, 10, 12.
 *   args[1] = "mapped" to use Mapped mesh in PML domain, anything else = FreeTri.
 *
 * Task 1 (mesh convergence): run with meshLevel=6, 8, 12 (FreeTri).
 *   -> Check z_max drift across mesh levels (should approach a converged value).
 *
 * Task 2 (PML swept mesh): run with meshLevel=8 + "mapped".
 *   -> Check z_max stays the same OR improves PML absorption.
 */
public class step1_v2_piston_pml_mesh {

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
        int meshLevel = 8;        // default lam/8
        boolean pmlMapped = false;
        if (args.length >= 1) {
            try { meshLevel = Integer.parseInt(args[0]); } catch (Exception e) {}
        }
        if (args.length >= 2 && args[1].equalsIgnoreCase("mapped")) pmlMapped = true;

        String tag = "step1_v2_pml_lam" + meshLevel + (pmlMapped ? "_mapped" : "");
        log("=== " + tag + ": mesh lam/" + meshLevel + ", PML " +
                (pmlMapped ? "Mapped" : "FreeTri") + " ===");

        ModelUtil.initStandalone(false);
        if (!ModelUtil.hasProduct("ACOUSTICS")) {
            log("!!! ACOUSTICS module NOT available. Abort.");
            return;
        }

        Model model = ModelUtil.create(tag);

        model.param().set("f0", "40[kHz]");
        model.param().set("c_air", "340[m/s]");
        model.param().set("lam", "c_air/f0");
        model.param().set("F_design", "120[mm]");
        model.param().set("p0", "1[Pa]");
        model.param().set("R_domain", "150[mm]");
        model.param().set("Z_up", "300[mm]");
        model.param().set("Z_down", "330[mm]");
        model.param().set("a_trans", "8[mm]");
        model.param().set("v_n", "2.5[mm/s]");
        double[] rho = {32.22, 45.96, 56.77, 66.10, 74.51, 82.28, 89.58, 96.52, 103.16, 109.57};
        for (int m = 1; m <= 10; m++) model.param().set("rho_" + m, rho[m - 1] + "[mm]");

        var comp = model.component().create("comp1");
        var geom = comp.geom().create("geom1", 2);
        geom.lengthUnit("mm");
        geom.axisymmetric(true);

        // Air main
        geom.create("air", "Rectangle");
        geom.feature("air").set("size", new String[]{"R_domain", "Z_up+(Z_down-20[mm])"});
        geom.feature("air").set("pos", new String[]{"0", "-Z_up"});

        // PML top
        geom.create("pml_top", "Rectangle");
        geom.feature("pml_top").set("size", new String[]{"R_domain", "20[mm]"});
        geom.feature("pml_top").set("pos", new String[]{"0", "Z_down-20[mm]"});

        // PML right
        geom.create("pml_right", "Rectangle");
        geom.feature("pml_right").set("size", new String[]{"20[mm]", "Z_up+(Z_down-20[mm])"});
        geom.feature("pml_right").set("pos", new String[]{"R_domain", "-Z_up"});

        // PML corner
        geom.create("pml_corner", "Rectangle");
        geom.feature("pml_corner").set("size", new String[]{"20[mm]", "20[mm]"});
        geom.feature("pml_corner").set("pos", new String[]{"R_domain", "Z_down-20[mm]"});

        // Union PML
        geom.create("pml_union", "Union");
        geom.feature("pml_union").selection("input").set(new String[]{"pml_top", "pml_right", "pml_corner"});

        // Point at source position
        geom.create("pt_src", "Point");
        geom.feature("pt_src").setIndex("p", "a_trans", 0);
        geom.feature("pt_src").setIndex("p", "-Z_up", 1);

        // 10 zp Rectangle walls
        double[] rhoArr = new double[11];
        rhoArr[0] = 0;
        for (int m = 1; m <= 10; m++) rhoArr[m] = rho[m - 1];
        double zHalfThick = 0.05;
        for (int m = 1; m <= 10; m++) {
            String tg = "zp_" + m;
            geom.create(tg, "Rectangle");
            geom.feature(tg).set("size", new String[]{
                "rho_" + m + "-" + (m > 1 ? "rho_" + (m - 1) : "0"),
                String.valueOf(2 * zHalfThick)
            });
            geom.feature(tg).set("pos", new String[]{
                m > 1 ? "rho_" + (m - 1) : "0",
                String.valueOf(-zHalfThick)
            });
        }

        // Difference even rings
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

        // Probe PML union domain
        int pml_domain_id = -1;
        try {
            Object selList = comp.getClass().getMethod("selection").invoke(comp);
            Object sel = selList.getClass().getMethod("create", String.class, String.class).invoke(selList, "probe_pml", "Box");
            sel.getClass().getMethod("set", String.class, String.class).invoke(sel, "entitydim", "2");
            sel.getClass().getMethod("set", String.class, String.class).invoke(sel, "xmin", "100");
            sel.getClass().getMethod("set", String.class, String.class).invoke(sel, "xmax", "160");
            sel.getClass().getMethod("set", String.class, String.class).invoke(sel, "ymin", "315");
            sel.getClass().getMethod("set", String.class, String.class).invoke(sel, "ymax", "325");
            int[] doms = (int[]) sel.getClass().getMethod("entities").invoke(sel);
            selList.getClass().getMethod("remove", String.class).invoke(selList, "probe_pml");
            if (doms != null && doms.length > 0) pml_domain_id = doms[0];
        } catch (Exception e) { log("PML probe failed: " + e.getMessage()); }

        if (pml_domain_id > 0) {
            try {
                model.component("comp1").coordSystem().create("pml1", "PML");
                model.component("comp1").coordSystem("pml1").set("ScalingType", "Cylindrical");
                model.component("comp1").coordSystem("pml1").selection().set(new int[]{pml_domain_id});
                log("OK pml1: PML coordSystem on domain id=" + pml_domain_id);
            } catch (Exception e) { log("FAIL pml1: " + e.getMessage()); }
        }

        // Probe boundaries
        int idSrc    = probeBoxFirst(comp, "p_src",    0.5,    7.5,   -300.05, -299.95);
        int idBaffle = probeBoxFirst(comp, "p_baffle", 80.0,   145.0, -300.05, -299.95);
        int idPMLouterTop    = probeBoxFirst(comp, "p_pmlt",    80.0,   145.0,  329.95,  330.05);
        int idPMLouterRight  = probeBoxFirst(comp, "p_pmlr",    169.95, 170.05, -100.0,  100.0);
        int idPMLouterCorner = probeBoxFirst(comp, "p_pmlc",    169.95, 170.05,  329.95,  330.05);

        log("Probed IDs: src=" + idSrc + ", baffle=" + idBaffle +
                ", pmlt=" + idPMLouterTop + ", pmlr=" + idPMLouterRight + ", pmlc=" + idPMLouterCorner);

        // BCs
        comp.physics("acpr").create("src", "NormalVelocity", 1);
        comp.physics("acpr").feature("src").selection().set(idSrc);
        comp.physics("acpr").feature("src").set("nvel", "v_n");

        comp.physics("acpr").create("baffle", "SoundHard", 1);
        comp.physics("acpr").feature("baffle").selection().set(idBaffle);

        comp.physics("acpr").create("pmlt_sh", "SoundHard", 1);
        comp.physics("acpr").feature("pmlt_sh").selection().set(idPMLouterTop);
        comp.physics("acpr").create("pmlr_sh", "SoundHard", 1);
        comp.physics("acpr").feature("pmlr_sh").selection().set(idPMLouterRight);
        comp.physics("acpr").create("pmlc_sh", "SoundHard", 1);
        comp.physics("acpr").feature("pmlc_sh").selection().set(idPMLouterCorner);

        // 5 EVEN rings SoundHard
        for (int m = 2; m <= 10; m += 2) {
            String bcn = "sh_" + m;
            comp.physics("acpr").create(bcn, "SoundHard", 1);
            double rIn = rhoArr[m - 1], rOut = rhoArr[m];
            double rMid = (rIn + rOut) / 2.0;
            int idZb = probeBoxFirst(comp, "zp_zb_" + m, rMid - 0.5, rMid + 0.5, -0.06, -0.04);
            int idZt = probeBoxFirst(comp, "zp_zt_" + m, rMid - 0.5, rMid + 0.5, 0.04, 0.06);
            int firstValid = (idZb > 0 ? idZb : (idZt > 0 ? idZt : -1));
            if (firstValid > 0) {
                comp.physics("acpr").feature(bcn).selection().set(firstValid);
            }
        }
        log("All BCs applied");

        // Mesh with task 1 variable meshLevel
        comp.mesh().create("mesh1", "geom1");
        if (pmlMapped) {
            // TASK 2 attempt v2: FreeTri + PML swept via edge refinement on PML outer 3 edges
            comp.mesh("mesh1").create("ftri", "FreeTri");
            comp.mesh("mesh1").feature("size").set("hmax", "lam/" + meshLevel);
            comp.mesh("mesh1").feature("size").set("hmin", "0.1[mm]");
            // Swept-style refinement: number of mesh layers on PML outer boundaries
            // (top z=330, right r=170, corner)
            comp.mesh("mesh1").create("ed_pml", "Edge");
            comp.mesh("mesh1").feature("ed_pml").selection().set(new int[]{idPMLouterTop, idPMLouterRight, idPMLouterCorner});
            comp.mesh("mesh1").feature("ed_pml").create("dis1", "Distribution");
            comp.mesh("mesh1").feature("ed_pml").feature("dis1").set("numelem", meshLevel);
            log("Mesh: FreeTri + PML-edges (top/right/corner) swept, " + meshLevel + " elements/edge");
        } else {
            // Standard FreeTri entire
            comp.mesh("mesh1").create("ftri", "FreeTri");
            comp.mesh("mesh1").feature("size").set("hmax", "lam/" + meshLevel);
            comp.mesh("mesh1").feature("size").set("hmin", "0.1[mm]");
            log("Mesh: FreeTri entire, h=lam/" + meshLevel);
        }
        comp.mesh("mesh1").run();

        // Study + solve
        model.study().create("std1");
        model.study("std1").create("freq", "Frequency");
        model.study("std1").feature("freq").set("plist", "f0");
        log("Solving...");
        try {
            model.study("std1").run();
            log("Solve completed");
        } catch (Exception e) {
            log("Solve failed: " + e.getMessage());
            return;
        }
        log("Model auto-saves to " + tag + "_" + tag + ".mph");
        log("=== done ===");
    }
}
