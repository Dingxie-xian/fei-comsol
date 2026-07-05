"""Final step1 plot — all 5 baselines including PML."""
import csv
import matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt

RESULTS = r'E:\comsol\minimaxcomsol\results'

def load_axis(name):
    rows = list(csv.reader(open(f'{RESULTS}/{name}', encoding='utf-8')))
    return [(float(r[0]), float(r[1])) for r in rows[1:]]

def focus(s, zmin=10):
    f = [(z,p) for z,p in s if z>=zmin]
    if not f: return None
    return max(f, key=lambda x: x[1])

plane = load_axis('axis_curve.csv')
default200 = load_axis('step1_v2_piston_axis.csv')
extended = load_axis('step1_v2_piston_extended_axis.csv')
pwr330 = load_axis('step1_v2_piston_pwr330_axis.csv')
pml = load_axis('step1_v2_piston_pml_axis.csv')

fp = focus(plane); fd = focus(default200); fe = focus(extended); fpwr = focus(pwr330); fpml = focus(pml)

# Detailed plot
fig, ax = plt.subplots(figsize=(14, 7))
ax.plot([z for z,_ in plane],     [p for _,p in plane],     'k-',  lw=2.5, alpha=0.9, label=f'plane_baseline (a=150, PWR, Z_down=200): z_max={fp[0]:.1f}mm, |p|={fp[1]:.2f}Pa')
ax.plot([z for z,_ in default200], [p for _,p in default200], 'r--', lw=1.5, alpha=0.55, label=f'piston PWR Z_down=200 (focus escapes domain): z_max={fd[0]:.1f}mm, |p|={fd[1]:.3f}Pa [reflected at z=200]')
ax.plot([z for z,_ in pwr330],    [p for _,p in pwr330],    'r-',  lw=2.0, alpha=0.85, label=f'piston PWR Z_down=330: z_max={fpwr[0]:.1f}mm, |p|={fpwr[1]:.3f}Pa')
ax.plot([z for z,_ in pml],       [p for _,p in pml],       'b-',  lw=2.0, alpha=0.85, label=f'piston PML Z_down=330 (15× stronger): z_max={fpml[0]:.1f}mm, |p|={fpml[1]:.3f}Pa')
ax.plot([z for z,_ in extended],  [p for _,p in extended],  'g:',  lw=1.5, alpha=0.55, label=f'piston PWR Z_down=350 (old): z_max={fe[0]:.1f}mm, |p|={fe[1]:.3f}Pa')

ax.axvline(120, color='gray', ls=':', alpha=0.4)
ax.text(122, 7.5, 'F_design = 120 mm\n(plane-wave peak)', color='gray', fontsize=9, bbox=dict(boxstyle='round', fc='w', alpha=0.85))
ax.axvline(285, color='purple', ls=':', alpha=0.5)
ax.text(287, 6.8, 'thin-lens eq = 200 mm\nparaxial -> full wave -> 285 mm (comsol)\n+12 mm PML adjustment', color='purple', fontsize=8, bbox=dict(boxstyle='round', fc='w', alpha=0.85))

ax.set_xlabel('z (mm)', fontsize=12)
ax.set_ylabel('|p| (Pa) at r=0', fontsize=12)
ax.set_title('Step 1 baselines: 5 runs — Z_down sweep + PML substitution — P0-7 evidence', fontsize=13, weight='bold')
ax.legend(loc='upper left', fontsize=9)
ax.grid(alpha=0.3)
ax.set_xlim(0, 340)
ax.set_ylim(0, 9.5)

# Summary boxes
ax.text(15, 8, 'P0-7 evidence two-piece set:\n  plane: (120.7, 8.21)\n  piston-PML: (297.2, 3.80)\n  -> geometric shift   +177 mm\n  -> energy ratio    /2.2',
        fontsize=10, color='black', bbox=dict(boxstyle='round,pad=0.5', fc='lightyellow', alpha=0.85))
ax.text(15, 3.5, 'PML vs PlaneWaveRadiation @ Z_down=330:\n  PWR:  (285, 0.25 Pa)  - reflection bias\n  PML:  (297, 3.80 Pa)  - perfect absorption\n  -> p_max x15 stronger with PML\n  -> z_max +12 mm shift to true focus',
        fontsize=10, color='blue', bbox=dict(boxstyle='round,pad=0.5', fc='lightcyan', alpha=0.85))

plt.tight_layout()
plt.savefig(f'{RESULTS}/step1_5baselines_final.png', dpi=120, bbox_inches='tight')
print('saved: step1_5baselines_final.png')

# Save summary
with open(f'{RESULTS}/step1_summary.csv', 'w') as f:
    f.write('baseline,z_max_mm,p_max_Pa,BC,Z_down\n')
    f.write(f'plane_baseline,{fp[0]:.3f},{fp[1]:.6f},PlaneWaveRadiation,200\n')
    f.write(f'piston_PWR_Z200,{fd[0]:.3f},{fd[1]:.6f},PlaneWaveRadiation,200\n')
    f.write(f'piston_PWR_Z330,{fpwr[0]:.3f},{fpwr[1]:.6f},PlaneWaveRadiation,330\n')
    f.write(f'piston_PWR_Z350_old,{fe[0]:.3f},{fe[1]:.6f},PlaneWaveRadiation,350\n')
    f.write(f'piston_PML_Z330,{fpml[0]:.3f},{fpml[1]:.6f},PML_20mm_geometric,330\n')
print('saved step1_summary.csv')
