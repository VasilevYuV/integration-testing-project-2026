namespace BigBangTests.Administration;

public sealed class CatalogAdminTests(ITestOutputHelper output)
{
    private static object Product(string name, decimal price = 12.50m) => new
    {
        name, description = "Temporary KT4 catalog entry", price,
        catalogBrandId = 1, catalogTypeId = 1, pictureUri = "eCatalog-item-default.png"
    };

    [Fact(DisplayName = "API01 Admin create, read, update and delete persist through SQL")]
    public async Task AdminLifecycle()
    {
        using var admin = new ShopSession(output, api: true);
        using var reader = new ShopSession(output, api: true);
        await admin.AuthenticateApi();
        var name = $"KT4-{Guid.NewGuid():N}";
        using var create = await admin.PostJson("/api/catalog-items", Product(name));
        Assert.Equal(HttpStatusCode.Created, create.StatusCode);
        var id = (await create.Content.ReadFromJsonAsync<JsonElement>()).GetProperty("catalogItem").GetProperty("id").GetInt32();
        try
        {
            var saved = (await reader.Json($"/api/catalog-items/{id}")).GetProperty("catalogItem");
            Assert.Equal(name, saved.GetProperty("name").GetString());
            Assert.Equal(12.50m, saved.GetProperty("price").GetDecimal());
            using var update = await admin.PutJson("/api/catalog-items", new
            {
                id, name = name + " updated", description = "Changed", price = 14.75m, catalogBrandId = 2, catalogTypeId = 2
            });
            Assert.Equal(HttpStatusCode.OK, update.StatusCode);
            saved = (await reader.Json($"/api/catalog-items/{id}")).GetProperty("catalogItem");
            Assert.Equal(name + " updated", saved.GetProperty("name").GetString());
            Assert.Equal(14.75m, saved.GetProperty("price").GetDecimal());
            Assert.Equal(2, saved.GetProperty("catalogBrandId").GetInt32());
        }
        finally
        {
            using var delete = await admin.Delete($"/api/catalog-items/{id}");
            Assert.Equal(HttpStatusCode.OK, delete.StatusCode);
        }
        using var missing = await reader.Get($"/api/catalog-items/{id}");
        Assert.Equal(HttpStatusCode.NotFound, missing.StatusCode);
    }

    [Fact(DisplayName = "API02 Anonymous and regular users cannot change the catalog")]
    public async Task AdminRoleRequired()
    {
        using var anonymous = new ShopSession(output, api: true);
        using var denied = await anonymous.PostJson("/api/catalog-items", Product("KT4-denied"));
        Assert.Equal(HttpStatusCode.Unauthorized, denied.StatusCode);
        using var regular = new ShopSession(output, api: true);
        await regular.AuthenticateApi("demouser@microsoft.com");
        using var forbidden = await regular.PostJson("/api/catalog-items", Product("KT4-denied"));
        Assert.Equal(HttpStatusCode.Forbidden, forbidden.StatusCode);
        var items = (await anonymous.Json("/api/catalog-items")).GetProperty("catalogItems").EnumerateArray();
        Assert.DoesNotContain(items, item => item.GetProperty("name").GetString() == "KT4-denied");
    }

    [Fact(DisplayName = "API03 Duplicate product names return 409 without creating a copy")]
    public async Task DuplicateNameRejected()
    {
        using var admin = new ShopSession(output, api: true);
        await admin.AuthenticateApi();
        using var response = await admin.PostJson("/api/catalog-items", Product(".NET Bot Black Sweatshirt"));
        Assert.Equal(HttpStatusCode.Conflict, response.StatusCode);
        var items = (await admin.Json("/api/catalog-items")).GetProperty("catalogItems").EnumerateArray();
        Assert.Single(items.Where(item => item.GetProperty("name").GetString() == ".NET Bot Black Sweatshirt"));
    }
}
