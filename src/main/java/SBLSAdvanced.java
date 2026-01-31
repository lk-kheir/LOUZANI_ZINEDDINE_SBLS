import org.chocosolver.solver.Model;
import org.chocosolver.solver.Solver;
import org.chocosolver.solver.search.strategy.Search;
import org.chocosolver.solver.variables.IntVar;

import java.util.ArrayList;
import java.util.List;

public class SBLSAdvanced {

    public static class Result {
        public final boolean sat;
        public final double time;
        public final long nodes;
        public final int[][] solution;

        public Result(boolean sat, double time, long nodes, int[][] solution) {
            this.sat = sat;
            this.time = time;
            this.nodes = nodes;
            this.solution = solution;
        }
    }

    public static Result solve(int n, int timeLimitSeconds) {

        Model model = new Model("SBLS-ADVANCED n=" + n);

        IntVar[][] grid = new IntVar[n][n];
        IntVar[][] posInRow = new IntVar[n][n];

        for (int r = 0; r < n; r++) {
            for (int c = 0; c < n; c++) {
                grid[r][c] = model.intVar("cell_" + r + "_" + c, 0, n - 1);
            }
            for (int v = 0; v < n; v++) {
                posInRow[r][v] = model.intVar("pos_" + r + "_" + v, 0, n - 1);
            }
        }

        // Latin square
        for (int r = 0; r < n; r++) {
            model.allDifferent(grid[r], "AC").post();
            model.inverseChanneling(grid[r], posInRow[r], 0, 0).post();
        }
        for (int c = 0; c < n; c++) {
            IntVar[] column = new IntVar[n];
            for (int r = 0; r < n; r++) column[r] = grid[r][c];
            model.allDifferent(column, "AC").post();
        }

        // Symmetry breaking
        for (int c = 0; c < n; c++) model.arithm(grid[0][c], "=", c).post();
        for (int r = 0; r < n; r++) model.arithm(grid[r][0], "=", r).post();

        // SBLS constraint
        int maxManhattan = 2 * (n - 1);
        int maxSum = n * n * maxManhattan;
        IntVar BAL = model.intVar("BAL", 0, maxSum);

        for (int a = 0; a < n; a++) {
            for (int b = a + 1; b < n; b++) {

                List<IntVar> distances = new ArrayList<>();

                for (int r1 = 0; r1 < n; r1++) {
                    for (int r2 = 0; r2 < n; r2++) {

                        int rowDist = Math.abs(r1 - r2);

                        IntVar colDist = model.intVar(0, n - 1);
                        model.distance(posInRow[r1][a], posInRow[r2][b], "=", colDist).post();

                        distances.add(model.intOffsetView(colDist, rowDist));
                    }
                }
                model.sum(distances.toArray(new IntVar[0]), "=", BAL).post();
            }
        }

        Solver solver = model.getSolver();
        solver.limitTime(timeLimitSeconds + "s");

        // dom/wdeg on position variables
        List<IntVar> vars = new ArrayList<>();
        for (int r = 1; r < n; r++)
            for (int v = 0; v < n; v++)
                vars.add(posInRow[r][v]);

        https://choco-solver.org/docs/solving/strategies/


        solver.setSearch(Search.domOverWDegSearch(vars.toArray(new IntVar[0])));

        boolean sat = solver.solve();

        int[][] sol = null;
        if (sat) {
            sol = new int[n][n];
            for (int r = 0; r < n; r++)
                for (int c = 0; c < n; c++)
                    sol[r][c] = grid[r][c].getValue();
        }

        return new Result(sat,
                solver.getMeasures().getTimeCount(),
                solver.getMeasures().getNodeCount(),
                sol);
    }
}
