import sys, os
import numpy as np
import mph

c = mph.Client(cores=1, version='6.4')
m = c.load(r'E:\comsol\minimaxcomsol\tests\step3_step3.mph')
print('name:', m.name())
print('parameters:', m.parameters())

# Get axial pressure via CutLine2D r=0, z=0..200
ds = m / 'datasets'
# already have cpl_axis. Let's find it.
for d in ds:
    print('  ds:', d.name(), d.tag())

# Eval axial profile
z = np.linspace(0.05, 199.95, 200)
xyz = np.column_stack([np.zeros_like(z), np.zeros_like(z), z])
p_abs = m.evaluate(['x','z','abs(acpr.p_t)'], dataset='dset1')  # 2x2x200 grid typically
print('eval shape:', np.array(p_abs).shape if hasattr(p_abs, 'shape') else 'not np array')

# Try the simpler CutLine2D explicitly
import mph.nodes
# Build a CutLine dataset programmatically
try:
    cl = m.evaluate('abs(acpr.p_t)', 'Pa', dataset='cpl_axis')
    print('axial cutline eval:', cl[:5] if hasattr(cl, '__getitem__') else cl)
except Exception as e:
    print('eval cutline failed:', e)

# Get coordinates too
print('---')
# Try direct dataset eval
try:
    out = m.java.apiModel().result().dataset("cpl_axis")
except Exception as e:
    print('cpl_axis dataset:', e)

c.remove(m)
