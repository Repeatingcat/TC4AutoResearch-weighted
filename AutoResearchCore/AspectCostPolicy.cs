namespace AutoResearch;

public static class AspectCostPolicy
{
    public const int FallbackCost = 16;

    private static Dictionary<string, int> costs = new(StringComparer.OrdinalIgnoreCase);

    public static void Configure(string? serializedCosts)
    {
        var parsed = new Dictionary<string, int>(StringComparer.OrdinalIgnoreCase);

        if (!string.IsNullOrWhiteSpace(serializedCosts))
        {
            foreach (var entry in serializedCosts.Split('&', StringSplitOptions.RemoveEmptyEntries))
            {
                var separator = entry.LastIndexOf(':');
                if (separator <= 0 || separator == entry.Length - 1)
                    continue;

                var tag = entry[..separator].Trim();
                if (tag.Length == 0 || !int.TryParse(entry[(separator + 1)..], out var cost) || cost <= 0)
                    continue;

                parsed[tag] = cost;
            }
        }

        costs = parsed;
    }

    public static int GetCost(string tag)
    {
        return costs.TryGetValue(tag, out var cost) ? cost : FallbackCost;
    }

    public static long CalculateCost(IEnumerable<string> aspects)
    {
        return aspects.Aggregate<string, long>(0, (total, tag) => total + GetCost(tag));
    }

    public static IOrderedEnumerable<string> OrderCandidates(
        IEnumerable<string> candidates,
        IReadOnlyDictionary<string, int> inventory,
        IReadOnlyCollection<string>? alreadyUsed = null)
    {
        return candidates
            .Distinct(StringComparer.OrdinalIgnoreCase)
            .OrderBy(GetCost)
            .ThenBy(tag => alreadyUsed?.Contains(tag) == true)
            .ThenByDescending(tag => inventory.TryGetValue(tag, out var amount) ? amount : 0)
            .ThenBy(tag => tag, StringComparer.OrdinalIgnoreCase);
    }
}
