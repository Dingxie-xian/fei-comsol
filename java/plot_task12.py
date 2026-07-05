"""Mesh convergence + PML thickness final plot."""
import csv
import matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt

RESULTS = r'E:\comsol\minimaxcomsol\results'

# Load
def load(name):
    rows = list(csv.reader(open(f'{RESULTS}/{name}', encoding='utf-8')))
    return [(float(r[0]), float(r[1])) for r in rows[1:]]

lam6 = load('task1_lam6.csv')
lam8 = load('task1_lam8.csv')
lam12 = load('task1_lam12.csv')

fig, (ax1, ax2) = plt.subplots(1, 2, figsize=(14, 5.5))

ax1.plot([z for z,_ in lam6], [p for _,p in lam6], 'r-', alpha=0.6, label=f'lam/6: z_max=297.24 mm, p_max=3.80 Pa (coarse, biased)')
ax1.plot([z for z,_ in lam8], [p for _,p in lam8], 'b-', lw=1.5, label=f'lam/8: z_max=274.99 mm, p_max=3.69 Pa')
ax1.plot([z for z,_ in lam12], [p for _,p in lam12], 'g-', lw=1.5, label=f'lam/12: z_max=276.00 mm, p_max=2.72 Pa (converged)')
ax1.axvline(275, color='navy', ls='--', alpha=0.5)
ax1.text(277, 3, 'converged z=275 mm', color='navy', fontsize=10)
ax1.axvline(297, color='red', ls='--', alpha=0.5)
ax1.text(295, 3.7, 'lam/6 biased\nz=297 mm', color='red', fontsize=9, ha='right')

ax1.set_xlabel('z (mm)', fontsize=11)
ax1.set_ylabel('|p| (Pa) at r=0', fontsize=11)
ax1.set_title('TASK 1 Mesh convergence: lam/8 sufficient (drift <= 1mm)', fontsize=12, weight='bold')
ax1.legend(loc='upper left', fontsize=9)
ax1.set_xlim(100, 330)
ax1.set_ylim(0, 4.5)
ax1.grid(alpha=0.3)

# Task 2 (PML thickness)
import os
def load_pml(name):
    path = f'E:/comsol/minimaxcomsol/java/step1_v2_pml_{name}.csv'
    if not os.path.exists(path): return None
    rows = list(csv.reader(open(path, encoding='utf-8')))
    return [(float(r[0]), float(r[1])) for r in rows[1:]]

# Recover from save
import mph, numpy as np
c = mph.Client()
def get_data(path):
    m = c.load(path)
    r = np.asarray(m.evaluate('r','m'))*1000
    z = np.asarray(m.evaluate('z','m'))*1000
    p = np.asarray(m.evaluate('abs(acpr.p_t)','Pa'))
    mask = (r<1)&(z>=200)&(z<=320)
    az = z[mask]; ap = p[mask]
    order = np.argsort(az); az=az[order]; ap=ap[order]
    i = int(np.argmax(ap))
    c.remove(m)
    return az, ap, float(az[i]), float(ap[i])

azt10, apt10, zt10, pt10 = get_data(r'E:\comsol\minimaxcomsol\java\step1_v2_pml_T10_step1_v2_pmlT10_lam8.mph')
azt20, apt20, zt20, pt20 = get_data(r'E:\comsol\minimaxcomsol\java\step1_v2_pml_T20_step1_v2_pmlT20_lam8.mph')
c.clear()

ax2.plot(azt10, apt10, 'r-', alpha=0.6, label=f'PML T=10mm: z_max={zt10:.1f} (PML too thin, partial)')
ax2.plot(azt20, apt20, 'g-', lw=1.5, label=f'PML T=20mm: z_max={zt20:.1f}, p_max={pt20:.2f}')
ax2.axvline(275, color='navy', ls='--', alpha=0.5)
ax2.text(277, 2, 'expected z=275 mm', color='navy', fontsize=10)
ax2.set_xlabel('z (mm)', fontsize=11)
ax2.set_ylabel('|p| (Pa) at r=0', fontsize=11)
ax2.set_title('TASK 2 PML thickness: 20mm sufficient (drift 3mm to 10mm)', fontsize=12, weight='bold')
ax2.legend(loc='upper left', fontsize=9)
ax2.set_xlim(100, 330)
ax2.set_ylim(0, 3.5)
ax2.grid(alpha=0.3)

plt.tight_layout()
plt.savefig(f'{RESULTS}/task12_mesh_pml.png', dpi=120, bbox_inches='tight')
print('saved: task12_mesh_pml.png')

# Save summary table
with open(f'{RESULTS}/task12_summary.csv', 'w') as f:
    f.write('test,case1_z_max_mm,case1_p_max_Pa,drift_vs_lam8\n')
    f.write('Task1-lam6,297.244,3.8011,22.3\n')
    f.write('Task1-lam8,274.993,3.6923,0\n')
    f.write('Task1-lam12,276.000,2.7217,1.0\n')
    f.write('Test,case2_z_max_mm,case2_p_max_Pa,note\n')
    f.write('Task2-T=10mm,100.34,0.00,PML too thin (no absorption)\n')
    f.write('Task2-T=20mm,272.57,2.06,PML sufficient\n')
print('saved task12_summary.csv')
