namespace BigBangTests.Identity;

public sealed class IdentityTests(ITestOutputHelper output)
{
    [Fact(DisplayName = "AUTH01 Login transfers the anonymous basket to the SQL account")]
    public async Task LoginTransfersBasket()
    {
        using var registration = new ShopSession(output);
        var email = await registration.Register();
        using var visitor = new ShopSession(output);
        await visitor.Add(1);
        await visitor.Add(1);
        await visitor.Login(email);
        Assert.Equal(2, HtmlForm.Quantity(await visitor.Page("/Basket")));
        using var anotherSession = new ShopSession(output);
        await anotherSession.Login(email);
        Assert.Equal(2, HtmlForm.Quantity(await anotherSession.Page("/Basket")));
    }

    [Fact(DisplayName = "AUTH02 Guests and invalid credentials cannot access checkout")]
    public async Task CheckoutRequiresAuthentication()
    {
        using var visitor = new ShopSession(output);
        using var checkout = await visitor.Get("/Basket/Checkout");
        Assert.Equal(HttpStatusCode.Redirect, checkout.StatusCode);
        Assert.Contains("Login", checkout.Headers.Location!.OriginalString);
        var login = await visitor.Page("/Identity/Account/Login");
        using var wrong = await visitor.PostForm("/Identity/Account/Login", new()
        {
            ["__RequestVerificationToken"] = HtmlForm.Token(login),
            ["Input.Email"] = "absent-kt4@example.test", ["Input.Password"] = "Wrong@password1"
        });
        Assert.Equal(HttpStatusCode.OK, wrong.StatusCode);
        Assert.Contains("Invalid login attempt", await wrong.Content.ReadAsStringAsync());
        using var orders = await visitor.Get("/order/my-orders");
        Assert.Equal(HttpStatusCode.Redirect, orders.StatusCode);
    }
}
