import com.comsol.model.*;
import com.comsol.model.util.*;

import java.util.Arrays;

/**
 * Step 4 master template. Run with CLI args: N f0_Hz [defect_m]
 *
 * Examples:
 *   comsolbatch step4.class 5  40000 -1   (N=5,  f=40kHz, no defect)
 *   comsolbatch step4.class 10 40000 -1   (baseline = step2)
 *   comsolbatch step4.class 10 40000  5   (defective ring m=5)
 *
 * Computes zone plate geometry from N and f0 (lam = c_air/f0).
 * ρ_m = √(m·λ/2 · (2·F + m·λ/2)),  with F = 120 mm.
 * All EVEN rings (plus defect ring) are blocked (SoundHard);
 * ODD rings are open (default Continuity).
 *
 * Mesh: uniform lam/6 global (no local refinement - baseline focus moves).
 * BCs: PlaneWaveRadiation on top + right (matches step2).
 */
public class step4 {

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
        // Parse args
        int N = 10;
        double f0_Hz = 40000;
        int defectRing = -1;
        if (args.length >= 1) N = Integer.parseInt(args[0]);
        if (args.length >= 2) f0_Hz = Double.parseDouble(args[1]);
        if (args.length >= 3) defectRing = Integer.parseInt(args[2]);

        String tag = "step4_N" + N + "_f" + (int)f0_Hz + (defectRing > 0 ? "_def" + defectRing : "");
        log("=== step4: " + tag + " ===");

        ModelUtil.initStandalone(false);
        if (!ModelUtil.hasProduct("ACOUSTICS")) {
            log("!!! ACOUSTICS module NOT available. Abort.");
            return;
        }

        Model model = ModelUtil.create(tag);

        model.param().set("f0", f0_Hz + "[Hz]");
        model.param().set("c_air", "340[m/s]");
        model.param().set("lam", "c_air/f0");
        model.param().set("F_design", "120[mm]");
        model.param().set("p0", "1[Pa]");
        model.param().set("R_domain", "150[mm]");
        model.param().set("Z_up", "300[mm]");
        model.param().set("a_trans", "8[mm]");
        model.param().set("Z_down", "330[mm]");   // match step1 PML/PWR @ Z_down=330
        model.param().set("v_n", "2.5[mm/s]");

        // Compute ring radii ρ_m = sqrt(m·λ/2 · (2F + m·λ/2)) for m = 1..N
        double lam = 340.0 / f0_Hz;   // meters
        double F = 0.120;             // meters
        double[] rho = new double[N + 1];  // index = m
        rho[0] = 0;
        for (int m = 1; m <= N; m++) {
            double msq_lam2 = m * lam / 2.0;
            rho[m] = Math.sqrt(msq_lam2 * (2 * F + msq_lam2)) * 1000.0;  // mm
            model.param().set("rho_" + m, rho[m] + "[mm]");
        }
        log("rho[m=1..N]: " + Arrays.toString(Arrays.copyOfRange(rho, 1, N + 1)));

        // Determine which rings to block (even + defect)
        boolean[] blocked = new boolean[N + 1];
        for (int m = 2; m <= N; m += 2) blocked[m] = true;
        if (defectRing >= 1 && defectRing <= N) blocked[defectRing] = true;
        int nBlocked = 0;
        for (int m = 1; m <= N; m++) if (blocked[m]) nBlocked++;
        log("blocked rings (SoundHard): " + nBlocked + " of " + N +
                (defectRing > 0 ? " (defect m=" + defectRing + ")" : ""));

        // Outer ring radius = rho[N]
        double rhoOuter = rho[N];

        var comp = model.component().create("comp1");
        var geom = comp.geom().create("geom1", 2);
        geom.lengthUnit("mm");
        geom.axisymmetric(true);

        // Air rectangle, size = R_domain x (Z_up + Z_down) — domain fixed; rho_outer
        // is always <= R_domain for our params (F=120mm, N<=20, f>=30 kHz)
        // For N=20, f=30 kHz: ρ_20 = sqrt(20·0.0113·(0.24+0.113))/1 = sqrt(0.0207) ≈ 0.144 m
        // = 144 mm < R_domain (150 mm) OK
        // For N=20, f=50 kHz: ρ_20 ≈ 0.0808 m = 80.8 mm OK
        // If rhoOuter > R_domain in pathological cases, we'd need R_domain >= rhoOuter.
        if (rhoOuter > 145.0) {
            double newR = rhoOuter + 10.0;
            log("WARNING: extending R_domain from 150 to " + newR + " mm (rho_" + N + "=" + rhoOuter + " mm)");
            model.param().set("R_domain", newR + "[mm]");
        }

        geom.create("air", "Rectangle");
        geom.feature("air").set("size", new String[]{"R_domain", "Z_up+Z_down"});
        geom.feature("air").set("pos", new String[]{"0", "-Z_up"});

        geom.create("pt_src", "Point");
        geom.feature("pt_src").setIndex("p", "a_trans", 0);
        geom.feature("pt_src").setIndex("p", "-Z_up", 1);

        // Ring zone-plate walls
        double zHalfThick = 0.05;
        for (int m = 1; m <= N; m++) {
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

        // Boolean difference: air - blocked zp walls
        geom.create("diff_blocked", "Difference");
        geom.feature("diff_blocked").set("intbnd", "on");
        geom.feature("diff_blocked").selection("input").set(new String[]{"air"});
        java.util.List<String> blockTags = new java.util.ArrayList<>();
        for (int m = 1; m <= N; m++) if (blocked[m]) blockTags.add("zp_" + m);
        String[] blockArr = blockTags.toArray(new String[0]);
        geom.feature("diff_blocked").selection("input2").set(blockArr);
        log("blocked zp Rectangle tags: " + Arrays.toString(blockArr));

        geom.run();
        log("Geometry built: domains=" + geom.getNDomains() + ", boundaries=" + geom.getNBoundaries());

        comp.material().create("mat1", "Common");
        comp.material("mat1").propertyGroup("def").set("density", "1.204[kg/m^3]");
        comp.material("mat1").propertyGroup("def").set("soundspeed", "c_air");
        comp.material("mat1").selection().all();

        comp.physics().create("acpr", "PressureAcoustics", "geom1");

        int idSrc    = probeBoxFirst(comp, "p_src",    0.5,    7.5,   -300.05, -299.95);
        int idBaffle = probeBoxFirst(comp, "p_baffle", 80.0,   145.0, -300.05, -299.95);
        int idTop    = probeBoxFirst(comp, "p_top",    80.0,   145.0,  329.95,  330.05);
        int idRight  = probeBoxFirst(comp, "p_right",  149.95, 150.05, -100.0,  100.0);

        log("Probed IDs: src=" + idSrc + ", baffle=" + idBaffle +
                ", top=" + idTop + ", right=" + idRight);

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
        } catch (Exception e) { log("FAIL src: " + e.getMessage()); allOk = false; }

        try {
            comp.physics("acpr").create("baffle", "SoundHard", 1);
            comp.physics("acpr").feature("baffle").selection().set(idBaffle);
            log("OK baffle: SoundHard on id=" + idBaffle);
        } catch (Exception e) { log("FAIL baffle: " + e.getMessage()); allOk = false; }

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

        // Apply SoundHard on each blocked ring (use first valid boundary ID per ring)
        for (int m = 1; m <= N; m++) {
            if (!blocked[m]) continue;
            try {
                String tg = "sh_" + m;
                comp.physics("acpr").create(tg, "SoundHard", 1);
                double rIn = rho[m - 1], rOut = rho[m];
                double rMid = (rIn + rOut) / 2.0;
                int idZb = probeBoxFirst(comp, "zp_zb_" + m, rMid - 0.5, rMid + 0.5, -0.06, -0.04);
                int idZt = probeBoxFirst(comp, "zp_zt_" + m, rMid - 0.5, rMid + 0.5, 0.04, 0.06);
                int idRin = probeBoxFirst(comp, "zp_rin_" + m, rIn - 0.05, rIn + 0.05, -0.04, 0.04);
                int idRout = probeBoxFirst(comp, "zp_rout_" + m, rOut - 0.05, rOut + 0.05, -0.04, 0.04);
                int firstValid = -1;
                int[] ids = {idZb, idZt, idRin, idRout};
                for (int id : ids) if (id > 0) { firstValid = id; break; }
                if (firstValid > 0) {
                    comp.physics("acpr").feature(tg).selection().set(firstValid);
                    log("OK sh_" + m + ": SoundHard on id=" + firstValid + " (rho=" + rho[m] + ")");
                } else {
                    log("FAIL sh_" + m + ": no valid boundary ID");
                    allOk = false;
                }
            } catch (Exception e) { log("FAIL sh_" + m + ": " + e.getMessage()); allOk = false; }
        }

        if (!allOk) {
            log("!!! Critical BCs failed. Aborting before solve.");
            return;
        }
        log("All BCs applied OK");

        // Mesh: lam/6 global + lam/8 in focal region [180, 330]
        // (matches step2 v2 mesh + extends slightly for piston-source focus ~285 mm)
        comp.mesh().create("mesh1", "geom1");
        comp.mesh("mesh1").create("ftri", "FreeTri");
        comp.mesh("mesh1").feature("size").set("hmax", "lam/6");
        comp.mesh("mesh1").feature("size").set("hmin", "0.1[mm]");
        // Local mesh refinement in z ∈ [180, 330]
        comp.mesh("mesh1").create("sz_refine", "Size");
        comp.mesh("mesh1").feature("sz_refine").set("hmax", "lam/8");
        comp.mesh("mesh1").feature("sz_refine").set("hmin", "0.1[mm]");
        comp.mesh("mesh1").feature("sz_refine").selection().geom("geom1", 2);
        comp.mesh("mesh1").feature("sz_refine").selection().set(new int[]{1});
        comp.mesh("mesh1").run();
        log("Mesh: lam/6 global + lam/8 local in z=[180,330] (matches step2 baseline mesh)");

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

        log("Model will be auto-saved by comsolbatch to <cwd>/" + tag + "_" + tag + ".mph");
        log("=== done: " + tag + " ===");
    }
}
