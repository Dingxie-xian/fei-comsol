"""Verify all step4 variants: extract z_max, p_max, store to summary."""
import mph, numpy as np, time, os, csv, sys

CASES = [
    ("N5  (f=40kHz)",     r"E:\comsol\minimaxcomsol\tests\step4_N5_f40000_step4_N5_f40000.mph",     "step4_N5_axis.csv"),
    ("N15 (f=40kHz)",     r"E:\comsol\minimaxcomsol\tests\step4_N15_f40000_step4_N15_f40000.mph",   "step4_N15_axis.csv"),
    ("N20 (f=40kHz)",     r"E:\comsol\minimaxcomsol\tests\step4_N20_f40000_step4_N20_f40000.mph",   "step4_N20_axis.csv"),
    ("f30 (N=10)",        r"E:\comsol\minimaxcomsol\tests\step4_N10_f30000_step4_N10_f30000.mph",   "step4_f30_axis.csv"),
    ("f35 (N=10)",        r"E:\comsol\minimaxcomsol\tests\step4_N10_f35000_step4_N10_f35000.mph",   "step4_f35_axis.csv"),
    ("f45 (N=10)",        r"E:\comsol\minimaxcomsol\tests\step4_N10_f45000_step4_N10_f45000.mph",   "step4_f45_axis.csv"),
    ("f50 (N=10)",        r"E:\comsol\minimaxcomsol\tests\step4_N10_f50000_step4_N10_f50000.mph",   "step4_f50_axis.csv"),
    ("def m=5 (N=10 f40)",r"E:\comsol\minimaxcomsol\tests\step4_N10_f40000_def5_step4_N10_f40000_def5.mph", "step4_def5_axis.csv"),
    # baseline = step2
    ("step2 baseline (f40 N10)", r"E:\comsol\minimaxcomsol\models\step2_v2.mph",                       "step2_axis_ref.csv"),
]

results = []
t0 = time.time()
c = mph.Client()
for name, mph_path, csv_out in CASES:
    if not os.path.exists(mph_path):
        print(f"SKIP {name} — no mph: {mph_path}")
        continue
    try:
        m = c.load(mph_path)
        # Try to find the solution dataset
        datasets = list(m / 'datasets')
        dset_tag = None
        for d in datasets:
            t = d.tag() if hasattr(d, 'tag') else str(d)
            if 'dset' in str(t).lower() or t.startswith('dset'):
                dset_tag = t
                break
        eval_kwargs = {}
        if dset_tag:
            eval_kwargs['dataset'] = dset_tag
        try:
            r = m.evaluate('r', 'm', **eval_kwargs)
            z = m.evaluate('z', 'm', **eval_kwargs)
            p = m.evaluate('abs(acpr.p_t)', 'Pa', **eval_kwargs)
        except Exception:
            r = m.evaluate('r', 'm')
            z = m.evaluate('z', 'm')
            p = m.evaluate('abs(acpr.p_t)', 'Pa')
        r_mm = np.asarray(r) * 1000.0
        z_mm = np.asarray(z) * 1000.0
        p_arr = np.asarray(p)
        mask = (r_mm < 1.0) & (z_mm >= 100) & (z_mm <= 350)
        if not mask.any():
            print(f"{name}: no axial points in z=[100,350]; using broader mask")
            mask = (r_mm < 1.0) & (z_mm >= 0) & (z_mm <= 350)
        az = z_mm[mask]
        ap = p_arr[mask]
        order = np.argsort(az)
        az = az[order]; ap = ap[order]
        i_peak = int(np.argmax(ap))
        z_max = float(az[i_peak])
        p_max = float(ap[i_peak])

        out_path = os.path.join(r"E:\comsol\minimaxcomsol\results", csv_out)
        with open(out_path, 'w') as f:
            f.write('z_mm,p_abs_Pa\n')
            for zv, pv in zip(az, ap):
                f.write(f'{zv:.4f},{pv:.6f}\n')

        results.append((name, z_max, p_max, csv_out))
        print(f"{name}: z_max={z_max:.2f}mm, p_max={p_max:.4f}Pa (ax pts: {mask.sum()})")
        c.remove(m)
    except Exception as e:
        print(f"FAIL {name}: {e}")

c.clear()

# Save summary
print(f"\n=== SUMMARY (time: {time.time()-t0:.1f}s) ===")
with open(r'E:\comsol\minimaxcomsol\results\step4_summary.csv', 'w', newline='') as f:
    w = csv.writer(f)
    w.writerow(['case', 'z_max_mm', 'p_max_Pa', 'csv'])
    for r in results:
        w.writerow(r)
    # ASCII art table
print(f"{'case':<25}  {'z_max (mm)':>10}  {'p_max (Pa)':>10}")
for r in results:
    print(f"{r[0]:<25}  {r[1]:>10.2f}  {r[2]:>10.4f}")
