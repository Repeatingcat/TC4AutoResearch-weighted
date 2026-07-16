using AutoResearch;

static void Assert(bool condition, string message)
{
    if (!condition)
        throw new InvalidOperationException(message);
}

AspectCostPolicy.Configure("cheap:2&expensive:256&invalid:0&broken&cheap:4&");

Assert(AspectCostPolicy.GetCost("cheap") == 4, "The last valid duplicate should win.");
Assert(AspectCostPolicy.GetCost("expensive") == 256, "Configured costs should be returned.");
Assert(AspectCostPolicy.GetCost("missing") == AspectCostPolicy.FallbackCost, "Unknown aspects need a fallback.");
Assert(AspectCostPolicy.CalculateCost(["cheap", "expensive"]) == 260, "Solution costs should be additive.");

var inventory = new Dictionary<string, int>
{
    ["cheap"] = 1,
    ["expensive"] = 9999,
    ["same-a"] = 3,
    ["same-b"] = 9,
};
AspectCostPolicy.Configure("cheap:2&expensive:256&same-a:8&same-b:8&");

var ordered = AspectCostPolicy.OrderCandidates(["expensive", "cheap"], inventory).ToArray();
Assert(ordered.SequenceEqual(["cheap", "expensive"]), "Cost must take priority over inventory size.");

ordered = AspectCostPolicy.OrderCandidates(["same-a", "same-b"], inventory).ToArray();
Assert(ordered.SequenceEqual(["same-b", "same-a"]), "Inventory should break equal-cost ties.");

ordered = AspectCostPolicy.OrderCandidates(["same-a", "same-b"], inventory, ["same-b"]).ToArray();
Assert(ordered.SequenceEqual(["same-a", "same-b"]), "Unused aspects should break remaining ties.");

var hexes = new[] { new Hex(0, 1), new Hex(0, 0), new Hex(0, -1) };
var targets = new Dictionary<Hex, string>
{
    [hexes[0]] = "x",
    [hexes[2]] = "y",
};
var aspectMap = new Dictionary<string, List<string>>
{
    ["x"] = ["c", "d"],
    ["y"] = ["c", "d"],
    ["c"] = ["x", "y"],
    ["d"] = ["x", "y"],
};

AspectCostPolicy.Configure("x:2&y:2&c:2&d:256&");
var exact = WeightedPathSolver.Solve(hexes, targets, aspectMap);
Assert(exact.HasSolution, "The exact solver should find the c path.");
Assert(exact.IsOptimal, "The bounded two-terminal search should prove this small solution optimal.");
Assert(exact.Solution.Single().Value == "c", "The exact solver should choose the cheaper c path.");

AspectCostPolicy.Configure("x:2&y:2&c:256&d:2&");
exact = WeightedPathSolver.Solve(hexes, targets, aspectMap);
Assert(exact.HasSolution, "The exact solver should find the d path.");
Assert(exact.IsOptimal, "The reversed two-terminal solution should also be proven optimal.");
Assert(exact.Solution.Single().Value == "d", "Reversing costs should select the d path.");

Console.WriteLine("AspectCostPolicy tests passed.");
