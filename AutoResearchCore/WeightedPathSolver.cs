using System.Diagnostics;

namespace AutoResearch;

public static class WeightedPathSolver
{
    public sealed record Result(
        bool HasSolution,
        Dictionary<Hex, string> Solution,
        bool IsOptimal,
        int ExpandedStates,
        int GeneratedStates,
        long ElapsedMilliseconds);

    private readonly record struct SearchState(int Position, string Aspect, ulong Visited);

    public static Result Solve(
        IReadOnlyList<Hex> hexes,
        IReadOnlyDictionary<Hex, string> targets,
        IReadOnlyDictionary<string, List<string>> aspectMap,
        int timeBudgetMilliseconds = 750)
    {
        var stopwatch = Stopwatch.StartNew();
        if (targets.Count != 2 || hexes.Count == 0 || hexes.Count > 64)
            return Empty(stopwatch);

        var positions = hexes
            .Select((hex, index) => (hex, index))
            .ToDictionary(item => item.hex, item => item.index);
        var targetItems = targets.ToArray();
        if (!positions.TryGetValue(targetItems[0].Key, out var startPosition)
            || !positions.TryGetValue(targetItems[1].Key, out var goalPosition))
            return Empty(stopwatch);

        var start = new SearchState(startPosition, targetItems[0].Value, 1UL << startPosition);
        var distances = new Dictionary<SearchState, long> { [start] = 0 };
        var parents = new Dictionary<SearchState, SearchState>();
        var queue = new PriorityQueue<SearchState, long>();
        queue.Enqueue(start, 0);

        SearchState? bestGoal = null;
        long bestGoalCost = long.MaxValue;
        var expanded = 0;
        var generated = 1;

        while (queue.Count > 0 && stopwatch.ElapsedMilliseconds < timeBudgetMilliseconds)
        {
            queue.TryDequeue(out var current, out var queuedCost);
            if (!distances.TryGetValue(current, out var currentCost) || currentCost != queuedCost)
                continue;

            expanded++;
            if (current.Position == goalPosition
                && current.Aspect.Equals(targetItems[1].Value, StringComparison.OrdinalIgnoreCase))
                return BuildResult(current, parents, hexes, targets, true, expanded, generated, stopwatch);

            if (!aspectMap.TryGetValue(current.Aspect, out var compatibleAspects))
                continue;

            foreach (var neighbour in hexes[current.Position].GetNeighbors())
            {
                if (!positions.TryGetValue(neighbour, out var nextPosition))
                    continue;

                var bit = 1UL << nextPosition;
                if ((current.Visited & bit) != 0)
                    continue;

                if (targets.TryGetValue(neighbour, out var fixedAspect))
                {
                    if (nextPosition != goalPosition
                        || !compatibleAspects.Contains(fixedAspect, StringComparer.OrdinalIgnoreCase))
                        continue;

                    AddState(fixedAspect, 0);
                }
                else
                {
                    foreach (var nextAspect in compatibleAspects
                        .Distinct(StringComparer.OrdinalIgnoreCase)
                        .OrderBy(AspectCostPolicy.GetCost)
                        .ThenBy(tag => tag, StringComparer.OrdinalIgnoreCase))
                        AddState(nextAspect, AspectCostPolicy.GetCost(nextAspect));
                }

                void AddState(string nextAspect, int stepCost)
                {
                    var next = new SearchState(nextPosition, nextAspect, current.Visited | bit);
                    var nextCost = currentCost + stepCost;
                    if (nextCost >= bestGoalCost)
                        return;
                    if (distances.TryGetValue(next, out var knownCost) && knownCost <= nextCost)
                        return;

                    distances[next] = nextCost;
                    parents[next] = current;
                    queue.Enqueue(next, nextCost);
                    generated++;

                    if (nextPosition == goalPosition
                        && nextAspect.Equals(targetItems[1].Value, StringComparison.OrdinalIgnoreCase))
                    {
                        bestGoal = next;
                        bestGoalCost = nextCost;
                    }
                }
            }
        }

        return bestGoal is { } timedResult
            ? BuildResult(timedResult, parents, hexes, targets, false, expanded, generated, stopwatch)
            : new Result(false, new Dictionary<Hex, string>(), false, expanded, generated, stopwatch.ElapsedMilliseconds);
    }

    private static Result BuildResult(
        SearchState goal,
        IReadOnlyDictionary<SearchState, SearchState> parents,
        IReadOnlyList<Hex> hexes,
        IReadOnlyDictionary<Hex, string> targets,
        bool isOptimal,
        int expanded,
        int generated,
        Stopwatch stopwatch)
    {
        var solution = new Dictionary<Hex, string>();
        var current = goal;
        while (true)
        {
            var hex = hexes[current.Position];
            if (!targets.ContainsKey(hex))
                solution[hex] = current.Aspect;
            if (!parents.TryGetValue(current, out current))
                break;
        }

        return new Result(true, solution, isOptimal, expanded, generated, stopwatch.ElapsedMilliseconds);
    }

    private static Result Empty(Stopwatch stopwatch)
    {
        return new Result(false, new Dictionary<Hex, string>(), false, 0, 0, stopwatch.ElapsedMilliseconds);
    }
}
