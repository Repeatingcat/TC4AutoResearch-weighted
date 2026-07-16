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

Console.WriteLine("AspectCostPolicy tests passed.");
