"""Verify step1_v2_piston_pml (Z_down=330 + PML sub-domain) z_max."""
import mph, time, numpy as np, sys

c = mph.Client()
m = c.load(r'E:\comsol\minimaxcomsol\java\step1_v2_piston_pml_step1_v2_piston_pml.mph')
print(f'loaded: {m.name()}')
print(f'  params: f0={m.parameters().get("f0")}, a_trans={m.parameters().get("a_trans")}, Z_up={m.parameters().get("Z_up")}, Z_down={m.parameters().get("Z_down")}')

r = np.asarray(m.evaluate('r','m')) * 1000
z = np.asarray(m.evaluate('z','m')) * 1000
p = np.asarray(m.evaluate('abs(acpr.p_t)','Pa'))

# Find focus in z > 100 (skip source region)
mask = (r < 1.0) & (z >= 100) & (z <= 350)
az = z[mask]; ap = p[mask]
order = np.argsort(az); az = az[order]; ap = ap[order]
print(f'  axial points (r<1mm, z∈[100,350]): {mask.sum()}')

i_peak = int(np.argmax(ap))
z_max = float(az[i_peak]); p_max = float(ap[i_peak])
print(f'\n=== step1_v2_piston_PML RESULTS ===')
print(f'z_max = {z_max:.3f} mm, p_max = {p_max:.4f} Pa')
print(f'Acceptance [165, 290] mm: {"PASS" if 165<=z_max<=290 else "FAIL"} ({z_max-285:+.2f} mm vs theoretical 285)')

# Quick check: is z=170 still a peak (PWR reflection artifact)?
mask170 = (az > 165) & (az < 175)
if mask170.any():
    p_at_170 = float(ap[mask170].max())
    print(f'  |p| near z=170 mm (PWR reflection artifact region): {p_at_170:.4f} Pa')

# Save axis
import os
os.makedirs(r'E:\comsol\minimaxcomsol\results', exist_ok=True)
with open(r'E:\comsol\minimaxcomsol\results\step1_v2_piston_pml_axis.csv', 'w') as f:
    f.write('z_mm,p_abs_Pa\n')
    for zv, pv in zip(az, ap):
        f.write(f'{zv:.4f},{pv:.6f}\n')
np.savez(r'E:\comsol\minimaxcomsol\results\step1_v2_piston_pml_field.npz', r_pts=r, z_pts=z, p_pts=p)
print('saved CSV + NPZ')

c.clear()
sys.exit(0 if 165<=z_max<=290 else 2)
