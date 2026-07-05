"""Phase 2 verify: load step1.mph, extract axial + transverse curves via mph.evaluate."""
import mph, time, sys, numpy as np

t0 = time.time()
c = mph.Client()
print(f'[{time.time()-t0:.1f}s] COMSOL {c.version} ready')

m = c.load(r'E:\comsol\minimaxcomsol\models\step1.mph')
print(f'[{time.time()-t0:.1f}s] loaded {m.name()}')

# Get node coordinates and pressure field
r = m.evaluate('r', 'm')  # meters
z = m.evaluate('z', 'm')
p = m.evaluate('abs(acpr.p_t)', 'Pa')
print(f'[{time.time()-t0:.1f}s] r/z/p shape: {r.shape}, {z.shape}, {p.shape}')

# Convert to mm
r_mm = r * 1000.0
z_mm = z * 1000.0

# === Axial scan: points with r < 0.05 mm, z in [0, 200] mm ===
axial_mask = (r_mm < 0.05) & (z_mm >= 0) & (z_mm <= 200)
print(f'[{time.time()-t0:.1f}s] axial points: {axial_mask.sum()}')

axial_z = z_mm[axial_mask]
axial_p = p[axial_mask]
# Sort by z
sort_idx = np.argsort(axial_z)
axial_z = axial_z[sort_idx]
axial_p = axial_p[sort_idx]

# Find peak in focal region (z > 5)
mask_focal = axial_z > 5
if mask_focal.any():
    i_peak = np.argmax(axial_p[mask_focal])
    z_max = float(axial_z[mask_focal][i_peak])
    p_max = float(axial_p[mask_focal][i_peak])
else:
    z_max, p_max = -1, -1

in_range = 114 <= z_max <= 126
print(f'\n=== AXIAL RESULT ===')
print(f'z_max = {z_max:.2f} mm')
print(f'p_max = {p_max:.4f} Pa')
print(f'acceptance: z_max in [114, 126] mm = {in_range}')
print(f'deviation from F_design=120: {z_max - 120:+.2f} mm ({(z_max-120)/120*100:+.2f}%)')

# Save axis CSV
import os
os.makedirs(r'E:\comsol\minimaxcomsol\results', exist_ok=True)
with open(r'E:\comsol\minimaxcomsol\results\axis_curve.csv', 'w') as f:
    f.write('z_mm,p_abs_Pa\n')
    for zv, pv in zip(axial_z, axial_p):
        f.write(f'{zv:.4f},{pv:.6f}\n')
print(f'[{time.time()-t0:.1f}s] saved axis_curve.csv ({len(axial_z)} rows)')

# === Transverse at z=120 mm ===
trans_mask = (np.abs(z_mm - 120) < 0.5) & (r_mm >= 0) & (r_mm <= 60)
print(f'[{time.time()-t0:.1f}s] transverse points at z=120: {trans_mask.sum()}')

trans_r = r_mm[trans_mask]
trans_p = p[trans_mask]
sort_idx = np.argsort(trans_r)
trans_r = trans_r[sort_idx]
trans_p = trans_p[sort_idx]

with open(r'E:\comsol\minimaxcomsol\results\transverse_curve_at_z120.csv', 'w') as f:
    f.write('r_mm,p_abs_Pa\n')
    for rv, pv in zip(trans_r, trans_p):
        f.write(f'{rv:.4f},{pv:.6f}\n')
print(f'[{time.time()-t0:.1f}s] saved transverse_curve_at_z120.csv ({len(trans_r)} rows)')

# FWHM on transverse
if len(trans_p) > 0:
    pPeakT = float(np.max(trans_p))
    half = pPeakT / 2.0
    # Find leftmost r where p drops below half (assume peak at r=0)
    above = trans_p >= half
    if above.any():
        i_last_above = np.where(above)[0][-1]
        r_at_half_right = float(trans_r[i_last_above])
        fwhm = 2 * r_at_half_right
        print(f'\n=== TRANSVERSE RESULT ===')
        print(f'Peak p = {pPeakT:.4f} Pa')
        print(f'FWHM (transverse, approx) = {fwhm:.2f} mm')

# === 2D field for heatmap ===
print(f'\n[{time.time()-t0:.1f}s] sampling 2D field for heatmap...')
# Sample on a regular grid
nr, nz = 60, 100
r_grid = np.linspace(0, 60, nr)
z_grid = np.linspace(0, 200, nz)
# Save raw data points for plotting
np.savez(r'E:\comsol\minimaxcomsol\results\field_data.npz',
         r_pts=r_mm, z_pts=z_mm, p_pts=p)
print(f'[{time.time()-t0:.1f}s] saved field_data.npz')

c.clear()
print(f'[{time.time()-t0:.1f}s] done')
sys.exit(0 if in_range else 2)