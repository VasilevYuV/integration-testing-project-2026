namespace BigBangTests.Catalog;

public sealed class CatalogTests(ITestOutputHelper output)
{
    [Fact(DisplayName = "CAT01 Web and API show the same paged SQL catalog")]
    public async Task CatalogPagesAgree()
    {
        using var web = new ShopSession(output);
        using var api = new ShopSession(output, api: true);
        var seen = new List<int>();
        for (var page = 0; page < 2; page++)
        {
            // Web.Constants.ITEMS_PER_PAGE in the pinned upstream is 10.
            var catalog = await api.Json($"/api/catalog-items?pageSize=10&pageIndex={page}");
            Assert.Equal(2, catalog.GetProperty("pageCount").GetInt32());
            var items = catalog.GetProperty("catalogItems").EnumerateArray().ToArray();
            var html = await web.Page($"/?pageId={page}");
            var ids = items.Select(item => item.GetProperty("id").GetInt32()).ToArray();
            Assert.Equal(ids, HtmlForm.ProductIds(html));
            foreach (var item in items)
            {
                Assert.Contains(item.GetProperty("name").GetString()!, HtmlForm.Text(html));
                Assert.Contains(HtmlForm.Money(item.GetProperty("price").GetDecimal()), html);
            }
            seen.AddRange(ids);
        }
        Assert.Equal(12, seen.Distinct().Count());
    }

    [Fact(DisplayName = "CAT02 Brand and type filters agree across Web and API")]
    public async Task FiltersAgree()
    {
        using var web = new ShopSession(output);
        using var api = new ShopSession(output, api: true);
        var json = await api.Json("/api/catalog-items?catalogBrandId=2&catalogTypeId=2");
        var items = json.GetProperty("catalogItems").EnumerateArray().ToArray();
        Assert.NotEmpty(items);
        Assert.All(items, item =>
        {
            Assert.Equal(2, item.GetProperty("catalogBrandId").GetInt32());
            Assert.Equal(2, item.GetProperty("catalogTypeId").GetInt32());
        });
        var html = await web.Page("/?CatalogModel.BrandFilterApplied=2&CatalogModel.TypesFilterApplied=2");
        Assert.Equal(items.Select(item => item.GetProperty("id").GetInt32()), HtmlForm.ProductIds(html));
        var empty = await web.Page("/?CatalogModel.BrandFilterApplied=999999");
        Assert.Empty(HtmlForm.ProductIds(empty));
        Assert.Contains("THERE ARE NO RESULTS", empty);
    }

    [Fact(DisplayName = "CAT03 Missing catalog item returns 404")]
    public async Task MissingProduct()
    {
        using var api = new ShopSession(output, api: true);
        using var response = await api.Get("/api/catalog-items/2147483647");
        Assert.Equal(HttpStatusCode.NotFound, response.StatusCode);
    }
}
