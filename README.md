# SBLS – Spatially Balanced Latin Square

This project implements the SBLS problem using Constraint Programming with Choco Solver.

# Strategies
```text

============================================================
1) Variables and domains
============================================================

We model an SBLS of order n as an n×n grid (Latin square) whose values represent fertilizers/colors.

1.1 Main grid variables
- grid[r][c] : the fertilizer placed at row r, column c.
- Domain: {0, 1, ..., n−1}

In code:
  grid[r][c] = model.intVar("cell_r_c", 0, n-1)

1.2 Position (inverse) variables
To express distances efficiently we introduce “position of a value in a row” variables:

- posInRow[r][v] : the column index where fertilizer v appears in row r.
- Domain: {0, 1, ..., n−1}

In code:
  posInRow[r][v] = model.intVar("pos_r_v", 0, n-1)

Why do we need posInRow?
- SBLS requires distances between occurrences of fertilizers.
- Each fertilizer v occurs exactly once in each row (Latin square property).
- So, to know where v is in row r, we only need its column = posInRow[r][v].
This avoids expensive “scan all cells + reification” models.

============================================================
2) Constraints used (a)
============================================================

2.1 Latin square constraints
(1) Row allDifferent:
For every row r, all values in grid[r][0..n-1] must be different.

In code:
  model.allDifferent(grid[r], ...).post();

(2) Column allDifferent:
For every column c, the values grid[0..n-1][c] must be different.

In code:
  model.allDifferent(column, ...).post();

2.2 linking constraints
We must link grid and posInRow so that they are consistent.

We use:
  model.inverseChanneling(grid[r], posInRow[r], 0, 0).post();

Meaning (informal):
- If grid[r][c] = v, then posInRow[r][v] = c
- And conversely, if posInRow[r][v] = c, then grid[r][c] = v

This is a strong global link between the row representation and the inverse representation.

2.3 SBLS balance constraint (core of the project)
SBLS requires that for every pair of fertilizers (a, b), the total Manhattan distance between all occurrences
of a and all occurrences of b in the grid is the same constant.

Occurrences:
- Fertilizer a in row r1 is at (r1, posInRow[r1][a])
- Fertilizer b in row r2 is at (r2, posInRow[r2][b])

Manhattan distance between these two occurrences:
  |r1 − r2| + |posInRow[r1][a] − posInRow[r2][b]|

We sum this over all pairs of rows (r1, r2) (there are n×n such pairs).
That gives TotalDistance(a,b).

Then we enforce:
  TotalDistance(a,b) = BAL     for all a < b
So BAL is the common value shared by all fertilizer pairs.

In code:
- BAL is an IntVar:
    int maxManhattan = 2*(n-1)
    int maxSum = n*n*maxManhattan
    IntVar BAL = model.intVar("BAL", 0, maxSum)

- For each pair (a,b):
  - For each (r1,r2):
      rowDist = |r1-r2| (constant integer)
      colDist = |posInRow[r1][a] - posInRow[r2][b]| (an IntVar)
      dist = rowDist + colDist (IntVar view)
  - Sum all dist values equals BAL.

Important detail:
- colDist is built using Choco’s distance constraint:
    model.distance(posInRow[r1][a], posInRow[r2][b], "=", colDist).post();
- dist = rowDist + colDist is built with an offset view:
    model.intOffsetView(colDist, rowDist)

This is efficient because it avoids creating extra variables for dist where possible.

2.4 Symmetry breaking (Advanced only)
Latin squares have huge symmetry: you can permute symbols, rows, columns and still get an equivalent solution.
To reduce the search space, the advanced model fixes a “reduced Latin square” form:

- First row is fixed to 0..n-1:
    grid[0][c] = c
- First column is fixed to 0..n-1:
    grid[r][0] = r

In code:
  for c: model.arithm(grid[0][c], "=", c).post();
  for r: model.arithm(grid[r][0], "=", r).post();

This does not remove valid SBLS orders; it only removes symmetric duplicates.

============================================================
3) Filtering / propagation algorithms (b)
============================================================

3.1 allDifferent(..., "AC")
The code uses:
  model.allDifferent(vars, "AC")

“AC” means Arc Consistency propagation for allDifferent.
Effect:
- removes values early if they cannot appear without violating allDifferent.
This is stronger than weaker options like bound consistency.

3.2 inverseChanneling
inverseChanneling is a global constraint that propagates between:
- row representation grid[r][c]
and
- position representation posInRow[r][v]
It gives strong pruning because fixing one side immediately restricts the other side.

3.3 distance(x, y, "=", d)
The distance constraint maintains consistency between x, y, and d = |x - y|.
If domains of x or y shrink, d shrinks; and if d shrinks it can prune x/y as well.

3.4 sum(distances, "=", BAL)
The sum constraint propagates bounds/consistency between the list of distance variables and BAL.
Since BAL is shared by all pairs (a,b), it also links all pairs together and gives global pruning.

============================================================
4) Search strategies (c)
============================================================

4.1 Basic solver search (baseline)
- Variables: grid[r][c] (all grid cells)
- Strategy: inputOrderLBSearch
  Meaning:
  - choose variables in input order (row by row)
  - assign the lowest available value first

4.2 Advanced solver search
- Variables: posInRow[r][v] for r = 1..n-1 (row 0 is fixed by symmetry breaking)
- Strategy: domOverWDegSearch
  Meaning (informal):
  - chooses variables with small domain and high constraint “degree” (weighted degree)
  - tends to focus early on variables involved in conflicts

Why it helps:
- posInRow variables appear directly in the SBLS distance constraints, so branching on them
  often drives the balance constraints earlier.
- dom/wdeg is a good general-purpose heuristic for hard CP problems.
```

# Execution table

```textmate
--------------------------------------------------------------------------------------------
  n | BASIC: SAT time(s) nodes        | ADV: SAT time(s) nodes         | speedup (basic/adv)
--------------------------------------------------------------------------------------------
  3 | YES    0.004            4 | YES    0.001            1 |     5.21
  4 | YES    0.003            8 | YES    0.001            3 |     2.80
  5 | YES    0.004           12 | YES    0.002            6 |     2.41
  6 | YES    0.004           19 | YES    0.002           11 |     1.75
  7 | YES    0.006           28 | YES    0.005           20 |     1.21
  8 | YES    0.010           38 | YES    0.007           27 |     1.43
  9 | YES    0.015           46 | YES    0.028           36 |     0.55
 10 | YES    0.026           59 | YES    0.023           48 |     1.16
 11 | YES    0.046           72 | YES    0.027           68 |     1.72
 12 | YES    0.041           91 | YES    0.033           84 |     1.27
 13 | YES    0.073          108 | YES    0.056          101 |     1.29
 14 | YES    0.122          131 | YES    0.084          119 |     1.45
 15 | YES    0.148          154 | YES    0.103          145 |     1.43
 16 | YES    0.243          176 | YES    0.136          169 |     1.78
 17 | YES    0.269          191 | YES    0.205          192 |     1.31
 18 | YES    0.461          217 | YES    0.257          224 |     1.79
 19 | YES    0.488          240 | YES    0.355          253 |     1.38
 20 | YES    0.712          275 | YES    0.457          288 |     1.56
 21 | YES    0.915          301 | YES    0.598          322 |     1.53
 22 | YES    1.348          335 | YES    0.761          362 |     1.77
 23 | YES    1.975          368 | YES    1.063          381 |     1.86
 24 | YES    2.191          415 | YES    1.359          432 |     1.61
 25 | YES    2.794          452 | YES    1.588          467 |     1.76
 26 | YES    3.449          491 | YES    1.967          521 |     1.75
 27 | YES    4.403          521 | YES    2.648          566 |     1.66
 28 | YES    5.758          587 | YES    3.210          610 |     1.79
 29 | YES    6.616          610 | YES    4.067          664 |     1.63
 30 | YES    8.105          685 | YES    4.381          717 |     1.85
 31 | YES    9.573          736 | YES    5.482          768 |     1.75
 32 | YES   11.732          782 | YES    6.625          818 |     1.77
 33 | YES   13.540          809 | YES    7.647          870 |     1.77
 34 | YES   15.538          860 | YES    9.474          940 |     1.64
 35 | YES   18.121          902 | YES    9.992         1005 |     1.81
 36 | YES   21.205          973 | YES   12.020         1061 |     1.76
 37 | YES   28.060         1012 | YES   15.269         1129 |     1.84
 38 | YES   28.407         1079 | YES   15.271         1181 |     1.86
 39 | YES   30.877         1138 | YES   17.557         1262 |     1.76
 40 | YES   32.766         1223 | YES   19.642         1328 |     1.67
 41 | YES   42.012         1261 | YES   22.950         1406 |     1.83
 42 | YES   46.787         1342 | YES   26.217         1478 |     1.78
 43 | YES   55.821         1388 | YES   33.760         1551 |     1.65
 44 | YES   74.034         1487 | YES   32.127         1644 |     2.30
 45 | YES   72.828         1550 | YES   37.533         1722 |     1.94
 46 | YES   76.584         1631 | YES   42.144         1796 |     1.82
 47 | YES   88.449         1740 | YES   45.207         1877 |     1.96
 48 | YES  101.264         1819 | YES   49.599         1977 |     2.04
```


# Computer Architecutre
```bash
lscpu
Architecture:                x86_64
  CPU op-mode(s):            32-bit, 64-bit
  Address sizes:             46 bits physical, 48 bits virtual
  Byte Order:                Little Endian
CPU(s):                      20
  On-line CPU(s) list:       0-19
Vendor ID:                   GenuineIntel
  Model name:                13th Gen Intel(R) Core(TM) i9-13900H
    CPU family:              6
    Model:                   186
    Thread(s) per core:      2
    Core(s) per socket:      14
    Socket(s):               1
    Stepping:                2
    CPU(s) scaling MHz:      24%
    CPU max MHz:             5400.0000
    CPU min MHz:             400.0000
    BogoMIPS:                5990.40
    Flags:                   fpu vme de pse tsc msr pae mce cx8 apic sep mtrr pge mca cmov pat pse36 clflush dts acpi mmx fxsr sse sse2 ss ht tm pbe syscall nx pdpe1gb rdtscp lm constant_tsc art arch_perfmon pebs bts rep_good nopl xtopology n
                             onstop_tsc cpuid aperfmperf tsc_known_freq pni pclmulqdq dtes64 monitor ds_cpl vmx smx est tm2 ssse3 sdbg fma cx16 xtpr pdcm pcid sse4_1 sse4_2 x2apic movbe popcnt tsc_deadline_timer aes xsave avx f16c rdrand lahf
                             _lm abm 3dnowprefetch cpuid_fault epb ssbd ibrs ibpb stibp ibrs_enhanced tpr_shadow flexpriority ept vpid ept_ad fsgsbase tsc_adjust bmi1 avx2 smep bmi2 erms invpcid rdseed adx smap clflushopt clwb intel_pt sha_ni
                              xsaveopt xsavec xgetbv1 xsaves split_lock_detect user_shstk avx_vnni dtherm ida arat pln pts hwp hwp_notify hwp_act_window hwp_epp hwp_pkg_req hfi vnmi umip pku ospke waitpkg gfni vaes vpclmulqdq rdpid movdiri mo
                             vdir64b fsrm md_clear serialize pconfig arch_lbr ibt flush_l1d arch_capabilities
Virtualization features:     
  Virtualization:            VT-x
Caches (sum of all):         
  L1d:                       544 KiB (14 instances)
  L1i:                       704 KiB (14 instances)
  L2:                        11.5 MiB (8 instances)
  L3:                        24 MiB (1 instance)
NUMA:                        
  NUMA node(s):              1
  NUMA node0 CPU(s):         0-19
Vulnerabilities:             
  Gather data sampling:      Not affected
  Indirect target selection: Not affected
  Itlb multihit:             Not affected
  L1tf:                      Not affected
  Mds:                       Not affected
  Meltdown:                  Not affected
  Mmio stale data:           Not affected
  Reg file data sampling:    Mitigation; Clear Register File
  Retbleed:                  Not affected
  Spec rstack overflow:      Not affected
  Spec store bypass:         Mitigation; Speculative Store Bypass disabled via prctl
  Spectre v1:                Mitigation; usercopy/swapgs barriers and __user pointer sanitization
  Spectre v2:                Mitigation; Enhanced / Automatic IBRS; IBPB conditional; PBRSB-eIBRS SW sequence; BHI BHI_DIS_S
  Srbds:                     Not affected
  Tsa:                       Not affected
  Tsx async abort:           Not affected
  Vmscape:                   Mitigation; IBPB before exit to userspace
```