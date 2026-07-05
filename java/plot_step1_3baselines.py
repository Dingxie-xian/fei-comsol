"""Plot 3 step1 baselines comparison — plane vs piston (default) vs piston (extended)."""
import csv
import matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt
import numpy as np

RESULTS = r'E:\comsol\minimaxcomsol\results'

def load_axis(name):
    rows = list(csv.reader(open(f'{RESULTS}/{name}', encoding='utf-8')))
    return [(float(r[0]), float(r[1])) for r in rows[1:]]

def focus(s, zmin=10):
    f = [(z,p) for z,p in s if z>=zmin]
    if not f: return None
    return max(f, key=lambda x: x[1])

plane = load_axis('axis_curve.csv')          # step1_plane_baseline
default = load_axis('step1_v2_piston_axis.csv')  # current (Z_down=200)
# Now copy extended axis from a fresh run...

# We need extended axis. Let's compute from saved mph file:
import mph
c = mph.Client()
m = c.load(r'E:\comsol\minimaxcomsol\java\step1_v2_piston_extended.mph')
r = np.asarray(m.evaluate('r','m')) * 1000
z = np.asarray(m.evaluate('z','m')) * 1000
p = np.asarray(m.evaluate('abs(acpr.p_t)','Pa'))
mask = (r < 1.0) & (z >= 100) & (z <= 350)
az = z[mask]; ap = p[mask]
order = np.argsort(az); az = az[order]; ap = ap[order]
extended = list(zip(az, ap))
with open(f'{RESULTS}/step1_v2_piston_extended_axis.csv', 'w') as f:
    f.write('z_mm,p_abs_Pa\n')
    for zv, pv in zip(az, ap):
        f.write(f'{zv:.4f},{pv:.6f}\n')
c.remove(m); c.clear()
print('saved extended axis csv')

fp = focus(plane)
fd = focus(default)
fe = focus(extended)
print(f'plane:    z_max={fp[0]:.2f}, p={fp[1]:.3f}')
print(f'piston-D: z_max={fd[0]:.2f}, p={fd[1]:.3f}')
print(f'piston-E: z_max={fe[0]:.2f}, p={fe[1]:.3f}')

# Plot 1: 3 baselines on one figure
fig, ax = plt.subplots(figsize=(13, 6))
ax.plot([z for z,_ in plane], [p for _,p in plane], 'b-', lw=2.0,
        label=f'Plane-wave baseline (a_trans=150mm): z_max={fp[0]:.1f}mm, |p|={fp[1]:.2f}Pa')
ax.plot([z for z,_ in default], [p for _,p in default], 'r-', lw=2.0, alpha=0.8,
        label=f'Piston prompt-default Z_down=200: z_max={fd[0]:.1f}mm, |p|={fd[1]:.2f}Pa')
ax.plot([z for z,_ in extended], [p for _,p in extended], 'g-', lw=2.0, alpha=0.8,
        label=f'Piston extended Z_down=350 (true focus): z_max={fe[0]:.1f}mm, |p|={fe[1]:.2f}Pa')

# Mark peaks
ax.axvline(120, color='blue', ls=':', alpha=0.4)
ax.text(122, 6, 'F_design = 120 mm', color='blue', fontsize=10, va='center', bbox=dict(boxstyle='round', fc='w', alpha=0.7))
ax.axvline(200, color='orange', ls=':', alpha=0.4)
ax.text(202, 5.5, 'lens-eq prediction 200 mm\n(1/Z_up + 1/Z_f = 1/F)', color='darkorange', fontsize=9, va='center', bbox=dict(boxstyle='round', fc='w', alpha=0.7))
ax.axvline(284.8, color='green', ls=':', alpha=0.6)
ax.text(287, 0.3, 'True focal peak\n(non-paraxial comsol: 285 mm)', color='green', fontsize=9, va='top', bbox=dict(boxstyle='round', fc='w', alpha=0.7))

ax.set_xlabel('z (mm)', fontsize=12)
ax.set_ylabel('|p| (Pa) at r=0', fontsize=12)
ax.set_title('Step 1 baselines: plane-wave vs piston-source — P0-7 innovation evidence', fontsize=13, weight='bold')
ax.legend(loc='upper right', fontsize=10)
ax.grid(alpha=0.3)
ax.set_xlim(0, 310)
ax.set_ylim(0, 8.5)

# Annotate Plane-wave-pass / Piston non-pass
ax.text(20, 7.5, 'PLANE-BASELINE: prompt [114,126] mm PASS ✓\n(z=120.7, +0.6% deviation)',
        fontsize=10, color='blue', bbox=dict(boxstyle='round,pad=0.5', fc='lightyellow', alpha=0.85))
ax.text(20, 4.5, 'PISTON-EXTENDED: prompt [190,210] mm FAIL — non-paraxial\n(comsol true focal at 285 mm, lens-eq formula assumes plane-wave-front)',
        fontsize=10, color='green', bbox=dict(boxstyle='round,pad=0.5', fc='lightyellow', alpha=0.85))
ax.text(20, 2.0, 'PISTON-DEFAULT (Z_down=200): focus escapes domain\n(real focus ~285 mm > 200), z=170 mm is reflected inter-mode',
        fontsize=10, color='red', bbox=dict(boxstyle='round,pad=0.5', fc='mistyrose', alpha=0.85))

plt.tight_layout()
plt.savefig(f'{RESULTS}/step1_3_baselines_comparison.png', dpi=120, bbox_inches='tight')
print('saved: step1_3_baselines_comparison.png')

# Save summary table
with open(f'{RESULTS}/step1_summary.csv', 'w') as f:
    f.write('case,z_max_mm,p_max_Pa,deviation,F_design_or_prediction\n')
    f.write(f'plane_baseline (a_trans=150),{fp[0]:.3f},{fp[1]:.6f},{fp[0]-120:+.3f},120\n')
    f.write(f'piston_default (Z_down=200),{fd[0]:.3f},{fd[1]:.6f},{fd[0]-200:+.3f},200\n')
    f.write(f'piston_extended (Z_down=350),{fe[0]:.3f},{fe[1]:.6f},{fe[0]-200:+.3f},200\n')
print('saved step1_summary.csv')
