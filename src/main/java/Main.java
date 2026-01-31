import java.util.Scanner;

public class Main {

    private static final String ANSI_RESET = "\u001B[0m";

    // Unique label for any n (no wrapping)
    private static String labelOf(int v) {
        return "F" + v;
    }

    // Padding so columns stay aligned when printing F0..F39 etc.
    private static String pad(String s, int width) {
        return String.format("%" + width + "s", s);
    }

    // --------- Printing grids ---------

    private static void printSolutionPlain(int[][] sol) {
        int n = sol.length;
        int width = ("F" + (n - 1)).length();

        for (int r = 0; r < n; r++) {
            for (int c = 0; c < n; c++) {
                int v = sol[r][c];
                System.out.print(pad(labelOf(v), width) + " ");
            }
            System.out.println();
        }
    }

    // ANSI 256-color printing for ANY n
    private static void printSolutionColored(int[][] sol) {
        int n = sol.length;
        int width = ("F" + (n - 1)).length();

        for (int r = 0; r < n; r++) {
            for (int c = 0; c < n; c++) {
                int v = sol[r][c];
                int colorCode = colorFromIndex(v, n);
                String cell = pad(labelOf(v), width);
                System.out.print("\u001B[38;5;" + colorCode + "m" + cell + ANSI_RESET + " ");
            }
            System.out.println();
        }
    }

    private static int colorFromIndex(int index, int total) {
        double t = (total <= 1) ? 0.0 : (double) index / (double) (total - 1);
        return 16 + (int) Math.round(t * 200.0);
    }

    // --------- SBLS distance checking (post-solve verification) ---------

    private static int manhattan(int r1, int c1, int r2, int c2) {
        return Math.abs(r1 - r2) + Math.abs(c1 - c2);
    }

    private static int[][][] positionsByValue(int[][] sol) {
        int n = sol.length;
        int[][][] positions = new int[n][n][2];
        int[] count = new int[n];

        for (int r = 0; r < n; r++) {
            for (int c = 0; c < n; c++) {
                int v = sol[r][c];
                int k = count[v]++;
                positions[v][k][0] = r;
                positions[v][k][1] = c;
            }
        }
        return positions;
    }

    private static long totalDistanceForPair(int[][] sol, int a, int b) {
        int n = sol.length;
        int[][][] pos = positionsByValue(sol);

        long sum = 0L;
        for (int i = 0; i < n; i++) {
            int r1 = pos[a][i][0], c1 = pos[a][i][1];
            for (int j = 0; j < n; j++) {
                int r2 = pos[b][j][0], c2 = pos[b][j][1];
                sum += manhattan(r1, c1, r2, c2);
            }
        }
        return sum;
    }

    private static void printAllPairDistances(int[][] sol) {
        int n = sol.length;
        System.out.println("TotalDistance for each pair (should all be equal):");

        Long first = null;
        boolean allEqual = true;

        for (int a = 0; a < n; a++) {
            for (int b = a + 1; b < n; b++) {
                long sum = totalDistanceForPair(sol, a, b);
                System.out.printf("  %s-%s : %d%n", labelOf(a), labelOf(b), sum);

                if (first == null) first = sum;
                else if (sum != first) allEqual = false;
            }
        }

        if (first == null) {
            System.out.println("  (no pairs)");
        } else if (allEqual) {
            System.out.println("✅ All pair totals are equal. SBLS balance verified.");
        } else {
            System.out.println("❌ Pair totals are NOT all equal. Something is wrong.");
        }
    }

    // --------- Table helpers ---------

    private static void printRangeHeader() {
        System.out.println("--------------------------------------------------------------------------------------------");
        System.out.println("  n | BASIC: SAT time(s) nodes        | ADV: SAT time(s) nodes         | speedup (basic/adv)");
        System.out.println("--------------------------------------------------------------------------------------------");
    }

    private static void printRangeRow(int n, SBLSBasic.Result basic, SBLSAdvanced.Result adv) {
        String bSat = basic.sat ? "YES" : "NO";
        String aSat = adv.sat ? "YES" : "NO";

        double speedup = (adv.time > 1e-9) ? (basic.time / adv.time) : Double.POSITIVE_INFINITY;

        System.out.printf("%3d | %3s %8.3f %12d | %3s %8.3f %12d | %8.2f%n",
                n,
                bSat, basic.time, basic.nodes,
                aSat, adv.time, adv.nodes,
                speedup);
    }

    // --------- Main ---------

    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);

        System.out.print("Run one n or a range? (1 = one, 2 = range): ");
        int mode = sc.nextInt();

        System.out.print("Enter time limit per run (seconds): ");
        int timeLimit = sc.nextInt();

        if (mode == 2) {
            System.out.print("Enter start n (recommended >= 3): ");
            int nStart = sc.nextInt();

            System.out.print("Enter max n: ");
            int nMax = sc.nextInt();

            System.out.println();
            printRangeHeader();

            for (int n = nStart; n <= nMax; n++) {
                SBLSBasic.Result basic = SBLSBasic.solve(n, timeLimit);
                SBLSAdvanced.Result adv = SBLSAdvanced.solve(n, timeLimit);
                printRangeRow(n, basic, adv);
            }

            System.out.println("--------------------------------------------------------------------------------------------");
            return; // range mode: table only
        }

        // Single n mode (detailed output)
        System.out.print("Enter n: ");
        int n = sc.nextInt();

        System.out.print("Show solution grids? (y/n): ");
        boolean showGrids = sc.next().equalsIgnoreCase("y");

        System.out.print("Use colors when printing grids? (y/n): ");
        boolean useColors = sc.next().equalsIgnoreCase("y");

        System.out.print("Show total distance for each fertilizer pair? (y/n): ");
        boolean showPairTotals = sc.next().equalsIgnoreCase("y");

        System.out.println();
        System.out.println("------------------------------------------------------------");
        System.out.println(" Solver     | SAT |   time (s)   |   nodes explored");
        System.out.println("------------------------------------------------------------");

        SBLSBasic.Result basic = SBLSBasic.solve(n, timeLimit);
        System.out.printf(" %-10s | %-3s | %11.3f | %16d%n", "BASIC", basic.sat ? "YES" : "NO", basic.time, basic.nodes);

        SBLSAdvanced.Result adv = SBLSAdvanced.solve(n, timeLimit);
        System.out.printf(" %-10s | %-3s | %11.3f | %16d%n", "ADVANCED", adv.sat ? "YES" : "NO", adv.time, adv.nodes);

        System.out.println("------------------------------------------------------------");
        System.out.printf("TIME COMPARISON: BASIC %.3fs vs ADVANCED %.3fs%n", basic.time, adv.time);

        // Print and verify solutions
        if (showGrids || showPairTotals) {

            System.out.println("\n===== BASIC OUTPUT =====");
            if (basic.sat) {
                if (showGrids) {
                    System.out.println("Solution:");
                    if (useColors) printSolutionColored(basic.solution);
                    else printSolutionPlain(basic.solution);
                }
                if (showPairTotals) {
                    printAllPairDistances(basic.solution);
                }
            } else {
                System.out.println("No solution found within limits.");
            }

            System.out.println("\n===== ADVANCED OUTPUT =====");
            if (adv.sat) {
                if (showGrids) {
                    System.out.println("Solution:");
                    if (useColors) printSolutionColored(adv.solution);
                    else printSolutionPlain(adv.solution);
                }
                if (showPairTotals) {
                    printAllPairDistances(adv.solution);
                }
            } else {
                System.out.println("No solution found within limits.");
            }
        }
    }
}
