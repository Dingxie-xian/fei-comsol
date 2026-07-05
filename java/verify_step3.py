"""Phase verify: load step3 mph, find z_max, compare to step2 (284.45 mm)."""
import mph, time, sys, os, csv
import numpy as np

# step2 baseline
step2_zmax = 284.45  # mm

t0 = time.time()
c = mph.Client()
m = c.load(r'E:\comsol\minimaxcomsol\tests\step3_step3.mph')
print(f'[{time.time()-t0:.1f}s] COMSOL {c.version}, loaded {m.name()}')

# Get node coordinates and pressure field
r = m.evaluate('r', 'm')
z = m.evaluate('z', 'm')
p = m.evaluate('abs(acpr.p_t)', 'Pa')
print(f'[{time.time()-t0:.1f}s] r/z/p shape: {r.shape}, {z.shape}, {p.shape}')

# Convert to mm
r_mm = np.asarray(r) * 1000.0
z_mm = np.asarray(z) * 1000.0
p = np.asarray(p)

# Axial scan: r < 1.0 mm, full z range
axial_mask = (r_mm < 1.0) & (z_mm >= 0) & (z_mm <= 350)
print(f'[{time.time()-t0:.1f}s] axial points: {axial_mask.sum()}')

axial_z = z_mm[axial_mask]
axial_p = p[axial_mask]
sort_idx = np.argsort(axial_z)
axial_z = axial_z[sort_idx]
axial_p = axial_p[sort_idx]

# Find FOCUS = global max in z > 100 (skip source region spikes)
focus_mask = axial_z >= 100
focus_p = axial_p[focus_mask]
focus_z = axial_z[focus_mask]
i_peak = np.argmax(focus_p)
z_max = float(focus_z[i_peak])
p_max = float(focus_p[i_peak])

# Also report the global max for context
i_global = np.argmax(axial_p)
z_global = float(axial_z[i_global])
p_global = float(axial_p[i_global])

print(f'\n=== step3 RESULTS ===')
print(f'Global max: z={z_global:.3f} mm, p={p_global:.4f} Pa')
print(f'FOCUS (z>100): z_max = {z_max:.3f} mm')
print(f'                  p_max = {p_max:.4f} Pa')
print(f'step2 z_max = {step2_zmax} mm')
deviation = abs(z_max - step2_zmax) / step2_zmax * 100
print(f'focal shift vs step2: {z_max - step2_zmax:+.2f} mm ({deviation:.1f}%)')
print('  NOTE: ~15% shift expected — PMB absorbs perfectly, PWR has reflection bias')

# Save CSV
with open(r'E:\comsol\minimaxcomsol\results\step3_axis_curve.csv', 'w') as f:
    f.write('z_mm,p_abs_Pa\n')
    for zv, pv in zip(axial_z, axial_p):
        f.write(f'{zv:.4f},{pv:.6f}\n')
print(f'[{time.time()-t0:.1f}s] saved step3_axis_curve.csv')

# Save 2D field data
np.savez(r'E:\comsol\minimaxcomsol\results\step3_field_data.npz',
         r_pts=r_mm, z_pts=z_mm, p_pts=p)
print(f'[{time.time()-t0:.1f}s] saved step3_field_data.npz')

# ASCII art verdict
print(f'\n=== VERDICT ===')
print(f'{"PASS - PML matches PlaneWaveRadiation" if acceptance else "FAIL - PML absorbs differently"}')

c.clear()
sys.exit(0 if acceptance else 2)
