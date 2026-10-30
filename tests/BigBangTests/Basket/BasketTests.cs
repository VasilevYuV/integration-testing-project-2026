namespace BigBangTests.Basket;

public sealed class BasketTests(ITestOutputHelper output)
{
    [Fact(DisplayName = "BAS01 Repeated add, quantity update and zero removal persist")]
    public async Task BasketLifecycle()
    {
        using var shop = new ShopSession(output);
        await shop.Add(1);
        await shop.Add(1);
        Assert.Equal(2, HtmlForm.Quantity(await shop.Page("/Basket")));
        using var update = await shop.UpdateQuantity("3");
        Assert.Equal(HttpStatusCode.OK, update.StatusCode);
        var basket = await shop.Page("/Basket");
        Assert.Equal(3, HtmlForm.Quantity(basket));
        Assert.Contains("58.50", basket); // Seed item 1 costs 19.50.
        using var remove = await shop.UpdateQuantity("0");
        Assert.Equal(HttpStatusCode.OK, remove.StatusCode);
        Assert.Contains("Basket is empty", await shop.Page("/Basket"));
    }

    [Fact(DisplayName = "BAS02 Browser price cannot replace the SQL catalog price")]
    public async Task ServerControlsPrice()
    {
        using var shop = new ShopSession(output);
        await shop.Add(1, "0.01");
        var basket = await shop.Page("/Basket");
        Assert.Contains("19.50", basket);
        Assert.DoesNotContain("0.01", basket);
    }

    [Fact(DisplayName = "BAS03 Concurrent anonymous sessions have isolated baskets")]
    public async Task SessionsAreIsolated()
    {
        using var first = new ShopSession(output);
        using var second = new ShopSession(output);
        await Task.WhenAll(first.Add(1), second.Add(2));
        var a = await first.Page("/Basket");
        var b = await second.Page("/Basket");
        Assert.Contains(".NET Bot Black Sweatshirt", a);
        Assert.DoesNotContain(".NET Black &amp; White Mug", a);
        Assert.Contains(".NET Black &amp; White Mug", b);
        Assert.DoesNotContain(".NET Bot Black Sweatshirt", b);
    }

    [Fact(DisplayName = "BAS04 Negative quantity shows validation and preserves basket")]
    public async Task InvalidQuantityKeepsBasketVisible()
    {
        using var shop = new ShopSession(output);
        await shop.Add(1);
        using var response = await shop.UpdateQuantity("-1");
        Assert.Equal(HttpStatusCode.OK, response.StatusCode);
        var body = await response.Content.ReadAsStringAsync();
        output.WriteLine($"Validation response shows empty basket: {body.Contains("Basket is empty")}");
        Assert.Equal(1, HtmlForm.Quantity(await shop.Page("/Basket")));
        Assert.Contains("Quantity must be bigger than 0", body);
        Assert.Contains(".NET Bot Black Sweatshirt", body);
    }

    [Fact(DisplayName = "BAS05 POST without antiforgery token is rejected before storage")]
    public async Task ForgedFormIsRejected()
    {
        using var shop = new ShopSession(output);
        using var response = await shop.PostForm("/Basket", new() { ["id"] = "1" });
        Assert.Equal(HttpStatusCode.BadRequest, response.StatusCode);
        Assert.Contains("Basket is empty", await shop.Page("/Basket"));
    }
}
