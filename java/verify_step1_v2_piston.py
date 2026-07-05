"""Verify step1_v2_piston z_max, plot. Acceptance [190, 210] mm."""
import mph, time, numpy as np, sys

t0 = time.time()
c = mph.Client()
m = c.load(r'E:\comsol\minimaxcomsol\java\step1_v2_piston_default.mph')
print(f'[{time.time()-t0:.1f}s] COMSOL {c.version}, loaded {m.name()}')
print(f'  parameters: f0={m.parameters().get("f0")}, a_trans={m.parameters().get("a_trans")}, Z_up={m.parameters().get("Z_up")}, Z_down={m.parameters().get("Z_down")}')

r = m.evaluate('r', 'm'); z = m.evaluate('z', 'm'); p = m.evaluate('abs(acpr.p_t)', 'Pa')
r_mm = np.asarray(r) * 1000.0
z_mm = np.asarray(z) * 1000.0
p_arr = np.asarray(p)
print(f'[{time.time()-t0:.1f}s] r/z/p shape: {r_mm.shape}, {z_mm.shape}, {p_arr.shape}')

# Search for FOCUS = max on axis (r < 1mm) in z >= 100 (skip near-source spikes)
mask = (r_mm < 1.0) & (z_mm >= 100) & (z_mm <= 350)
az = z_mm[mask]; ap = p_arr[mask]
order = np.argsort(az); az = az[order]; ap = ap[order]
print(f'  axial points (r<1mm, z∈[100,350]): {mask.sum()}')

i_peak = int(np.argmax(ap))
z_max = float(az[i_peak]); p_max = float(ap[i_peak])

print(f'\n=== step1_v2_piston RESULTS ===')
print(f'z_max = {z_max:.3f} mm')
print(f'p_max = {p_max:.4f} Pa')
print()
print(f'Acceptance: z_max ∈ [190, 210] mm (lens-equation-prediction)')
print(f'  Result deviation: {z_max-200:+.3f} mm from prediction center (200 mm)')

in_acceptance = 190 <= z_max <= 210
print(f'  PASS = {in_acceptance}')

# Save axis curve & field data
import os
os.makedirs(r'E:\comsol\minimaxcomsol\results', exist_ok=True)
with open(r'E:\comsol\minimaxcomsol\results\step1_v2_piston_axis.csv', 'w') as f:
    f.write('z_mm,p_abs_Pa\n')
    for zv, pv in zip(az, ap):
        f.write(f'{zv:.4f},{pv:.6f}\n')
print(f'[{time.time()-t0:.1f}s] saved step1_v2_piston_axis.csv')
np.savez(r'E:\comsol\minimaxcomsol\results\step1_v2_piston_field.npz',
         r_pts=r_mm, z_pts=z_mm, p_pts=p_arr)

c.clear()
sys.exit(0 if in_acceptance else 2)
