"""Run 5 mesh-convergence + PML-mapped cases."""
import os, time, subprocess

CASES = [
    # (mesh_arg, mapped_arg, output_mph, log_file)
    ("6",  "",     "step1_v2_pml_lam6.mph",  "step1_v2_pml_lam6.log"),
    ("8",  "",     "step1_v2_pml_lam8.mph",  "step1_v2_pml_lam8.log"),
    ("12", "",     "step1_v2_pml_lam12.mph", "step1_v2_pml_lam12.log"),
    ("8",  "mapped", "step1_v2_pml_lam8_mapped.mph",  "step1_v2_pml_lam8_mapped.log"),
    ("12", "mapped", "step1_v2_pml_lam12_mapped.mph", "step1_v2_pml_lam12_mapped.log"),
]

COMSOL = r'D:\Program Files\COMSOL\COMSOL64\Multiphysics\bin\win64\comsolbatch.exe'
TESTDIR = r'E:\comsol\minimaxcomsol\java'

for mesh, mapped, mph, logf in CASES:
    if os.path.exists(os.path.join(TESTDIR, f"{mph.replace('.mph','')}_{mph.replace('.mph','')}.mph")) \
            and os.path.getsize(os.path.join(TESTDIR, f"{mph.replace('.mph','')}_{mph.replace('.mph','')}.mph")) > 1000000:
        print(f"SKIP {mph} (exists, large enough)")
        continue
    t0 = time.time()
    args = [COMSOL, '-inputfile', 'step1_v2_piston_pml_mesh.class',
            '-outputfile', mph, '-batchlog', logf, mesh]
    if mapped:
        args.append(mapped)
    print(f"\n=== Run mesh={mesh} mapped={'Y' if mapped else 'N'} : {time.strftime('%H:%M:%S')} ===")
    with open(logf, 'w') as f:
        ret = subprocess.run(args, stdout=f, stderr=subprocess.STDOUT, cwd=TESTDIR)
    dt = time.time() - t0
    print(f"  ret={ret.returncode}, {dt:.1f}s")

print("\n=== All done ===")
