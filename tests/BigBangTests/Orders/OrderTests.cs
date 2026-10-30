namespace BigBangTests.Orders;

public sealed class OrderTests(ITestOutputHelper output)
{
    [Fact(DisplayName = "ORD01 Checkout persists an order and clears the basket")]
    public async Task CheckoutPersistsOrder()
    {
        using var buyer = new ShopSession(output);
        var email = await buyer.Register();
        await buyer.Add(1);
        await buyer.Add(1);
        await buyer.Add(2);
        var orderId = await buyer.Checkout();
        using var freshLogin = new ShopSession(output);
        await freshLogin.Login(email);
        Assert.Equal(orderId, Assert.Single(HtmlForm.OrderIds(await freshLogin.Page("/order/my-orders"))));
        var detail = await freshLogin.Page($"/order/detail/{orderId}");
        Assert.Contains(".NET Bot Black Sweatshirt", detail);
        Assert.Contains(".NET Black &amp; White Mug", detail);
        Assert.Contains("47.50", detail); // 2 x 19.50 + 8.50.
        Assert.Contains("123 Main St.", detail);
        Assert.Contains("Basket is empty", await freshLogin.Page("/Basket"));
    }

    [Fact(DisplayName = "ORD02 Empty checkout does not create an order")]
    public async Task EmptyCheckout()
    {
        using var buyer = new ShopSession(output);
        await buyer.Register();
        // Empty checkout has no form; a valid token is obtained from the catalog.
        using var response = await buyer.PostForm("/Basket/Checkout", new()
        {
            ["__RequestVerificationToken"] = HtmlForm.Token(await buyer.Page("/"))
        });
        Assert.Equal(HttpStatusCode.Redirect, response.StatusCode);
        Assert.Equal("/Basket", response.Headers.Location?.OriginalString);
        Assert.Empty(HtmlForm.OrderIds(await buyer.Page("/order/my-orders")));
    }

    [Fact(DisplayName = "ORD03 Another account cannot read an order by its identifier")]
    public async Task OrderOwnershipIsEnforced()
    {
        using var owner = new ShopSession(output);
        var ownerEmail = await owner.Register();
        await owner.Add(1);
        var orderId = await owner.Checkout();
        using var other = new ShopSession(output);
        var otherEmail = await other.Register();
        Assert.Empty(HtmlForm.OrderIds(await other.Page("/order/my-orders")));
        using var response = await other.Get($"/order/detail/{orderId}");
        var body = await response.Content.ReadAsStringAsync();
        output.WriteLine($"Owner={ownerEmail}; requester={otherEmail}; order={orderId}; " +
            $"productVisible={body.Contains(".NET Bot Black Sweatshirt")}; addressVisible={body.Contains("123 Main St.")}");
        Assert.True(response.StatusCode is HttpStatusCode.BadRequest or HttpStatusCode.Forbidden or HttpStatusCode.NotFound,
            $"Other account must be denied order {orderId}; received HTTP {(int)response.StatusCode}.");
        Assert.DoesNotContain("123 Main St.", body);
    }
}
