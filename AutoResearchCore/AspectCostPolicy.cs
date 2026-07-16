namespace AutoResearch;

public static class AspectCostPolicy
{
    public const int FallbackCost = 16;
    private const long InventoryScale = 1_000_000L;
    private const long MissingInventoryPenalty = 1_000_000_000L;

    private static Dictionary<string, int> costs = new(StringComparer.OrdinalIgnoreCase);
    public static bool InventoryPriority { get; private set; }
    public static string PreferenceName => InventoryPriority ? "inventory" : "weighted";

    public static void Configure(string? serializedCosts, string? preference = null)
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
        InventoryPriority = string.Equals(preference, "inventory", StringComparison.OrdinalIgnoreCase);
    }

    public static int GetCost(string tag)
    {
        return costs.TryGetValue(tag, out var cost) ? cost : FallbackCost;
    }

    public static long CalculateCost(IEnumerable<string> aspects)
    {
        return aspects.Aggregate<string, long>(0, (total, tag) => total + GetCost(tag));
    }

    public static int CalculateShortage(
        IEnumerable<string> aspects,
        IReadOnlyDictionary<string, int> inventory)
    {
        return aspects
            .GroupBy(tag => tag, StringComparer.OrdinalIgnoreCase)
            .Sum(group => Math.Max(0, group.Count() - GetInventoryAmount(group.Key, inventory)));
    }

    public static long CalculateScarcity(
        IEnumerable<string> aspects,
        IReadOnlyDictionary<string, int> inventory)
    {
        return aspects
            .GroupBy(tag => tag, StringComparer.OrdinalIgnoreCase)
            .Aggregate(0L, (total, group) =>
                total + group.Count() * InventoryScale / (GetInventoryAmount(group.Key, inventory) + 1L));
    }

    public static long GetTraversalCost(string tag, IReadOnlyDictionary<string, int> inventory)
    {
        if (!InventoryPriority)
            return GetCost(tag);

        var available = GetInventoryAmount(tag, inventory);
        return available <= 0
            ? MissingInventoryPenalty + GetCost(tag)
            : InventoryScale / (available + 1L) + GetCost(tag);
    }

    public static IOrderedEnumerable<T> OrderSolutions<T>(
        IEnumerable<T> solutions,
        Func<T, IEnumerable<string>> aspects,
        IReadOnlyDictionary<string, int> inventory)
    {
        if (InventoryPriority)
        {
            return solutions
                .OrderBy(solution => CalculateShortage(aspects(solution), inventory))
                .ThenBy(solution => CalculateScarcity(aspects(solution), inventory))
                .ThenBy(solution => CalculateCost(aspects(solution)));
        }

        return solutions.OrderBy(solution => CalculateCost(aspects(solution)));
    }

    public static IOrderedEnumerable<string> OrderCandidates(
        IEnumerable<string> candidates,
        IReadOnlyDictionary<string, int> inventory,
        IReadOnlyCollection<string>? alreadyUsed = null)
    {
        var distinct = candidates.Distinct(StringComparer.OrdinalIgnoreCase);
        if (InventoryPriority)
        {
            return distinct
                .OrderBy(tag => GetRemainingInventory(tag, inventory, alreadyUsed) <= 0)
                .ThenByDescending(tag => GetRemainingInventory(tag, inventory, alreadyUsed))
                .ThenBy(GetCost)
                .ThenBy(tag => tag, StringComparer.OrdinalIgnoreCase);
        }

        return distinct.OrderBy(GetCost)
            .ThenBy(tag => alreadyUsed?.Contains(tag) == true)
            .ThenByDescending(tag => GetInventoryAmount(tag, inventory))
            .ThenBy(tag => tag, StringComparer.OrdinalIgnoreCase);
    }

    private static int GetRemainingInventory(
        string tag,
        IReadOnlyDictionary<string, int> inventory,
        IReadOnlyCollection<string>? alreadyUsed)
    {
        var used = alreadyUsed?.Count(value => value.Equals(tag, StringComparison.OrdinalIgnoreCase)) ?? 0;
        return GetInventoryAmount(tag, inventory) - used;
    }

    private static int GetInventoryAmount(string tag, IReadOnlyDictionary<string, int> inventory)
    {
        return inventory.TryGetValue(tag, out var amount) ? Math.Max(0, amount) : 0;
    }
}
