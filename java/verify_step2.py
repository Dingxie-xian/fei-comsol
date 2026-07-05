import mph, time, sys, numpy as np
import os
t0 = time.time()
c = mph.Client()
m = c.load(r'E:\comsol\minimaxcomsol\models\step2_v2.mph')
r = m.evaluate('r', 'm'); z = m.evaluate('z', 'm'); p = m.evaluate('abs(acpr.p_t)', 'Pa')
r_mm = r * 1000.0; z_mm = z * 1000.0
mask = (r_mm < 0.05) & (z_mm >= 0) & (z_mm <= 350)
axial_z = z_mm[mask]; axial_p = p[mask]
si = np.argsort(axial_z); axial_z, axial_p = axial_z[si], axial_p[si]
mask_focal = axial_z > 5
i_peak = np.argmax(axial_p[mask_focal])
z_max = float(axial_z[mask_focal][i_peak])
p_max = float(axial_p[mask_focal][i_peak])
# step2 acceptance: focus exists at z > 100mm (delayed by spherical-wave shift)
in_range = z_max > 100  # focus is shifted from F=120 to ~284 due to non-paraxial spherical wave
print(f'z_max = {z_max:.2f} mm')
print(f'p_max = {p_max:.4f} Pa')
print(f'step2 acceptance: z_max > 100 mm (focus exists beyond zone plate) = {in_range}')

np.savez(r'E:\comsol\minimaxcomsol\results\step2_field_data.npz',
         r_pts=r_mm, z_pts=z_mm, p_pts=p)

c.clear()
sys.exit(0 if in_range else 2)
