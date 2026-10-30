namespace BigBangTests.Support;

internal sealed class ShopSession : IDisposable
{
    public const string Password = "Test-Pass@word123";
    private readonly ITestOutputHelper output;
    private readonly HttpClient client;

    public ShopSession(ITestOutputHelper output, bool api = false)
    {
        this.output = output;
        var address = Environment.GetEnvironmentVariable(api ? "API_URL" : "WEB_URL")
            ?? (api ? "http://localhost:18302/" : "http://localhost:18301/");
        client = new HttpClient(new HttpClientHandler
        {
            AllowAutoRedirect = false,
            CookieContainer = new CookieContainer()
        }) { BaseAddress = new Uri(address), Timeout = TimeSpan.FromSeconds(30) };
    }

    public async Task<HttpResponseMessage> Send(HttpMethod method, string path, HttpContent? content = null)
    {
        using var request = new HttpRequestMessage(method, path) { Content = content };
        var watch = System.Diagnostics.Stopwatch.StartNew();
        var response = await client.SendAsync(request);
        // Credentials, cookies and JWT are deliberately absent from test output.
        output.WriteLine($"{method} {path} -> {(int)response.StatusCode} ({watch.ElapsedMilliseconds} ms)");
        return response;
    }

    public Task<HttpResponseMessage> Get(string path) => Send(HttpMethod.Get, path);
    public Task<HttpResponseMessage> PostForm(string path, Dictionary<string, string> fields) =>
        Send(HttpMethod.Post, path, new FormUrlEncodedContent(fields));
    public Task<HttpResponseMessage> PostJson(string path, object body) => Send(HttpMethod.Post, path, JsonContent.Create(body));
    public Task<HttpResponseMessage> PutJson(string path, object body) => Send(HttpMethod.Put, path, JsonContent.Create(body));
    public Task<HttpResponseMessage> Delete(string path) => Send(HttpMethod.Delete, path);

    public async Task<string> Page(string path)
    {
        using var response = await Get(path);
        Assert.Equal(HttpStatusCode.OK, response.StatusCode);
        return await response.Content.ReadAsStringAsync();
    }

    public async Task<JsonElement> Json(string path)
    {
        using var response = await Get(path);
        Assert.Equal(HttpStatusCode.OK, response.StatusCode);
        return await response.Content.ReadFromJsonAsync<JsonElement>();
    }

    public async Task<string> Register()
    {
        var email = $"kt4-{Guid.NewGuid():N}@example.test";
        var page = await Page("/Identity/Account/Register");
        using var response = await PostForm("/Identity/Account/Register", new()
        {
            ["__RequestVerificationToken"] = HtmlForm.Token(page),
            ["Input.Email"] = email,
            ["Input.Password"] = Password,
            ["Input.ConfirmPassword"] = Password
        });
        Assert.Equal(HttpStatusCode.Redirect, response.StatusCode);
        output.WriteLine($"Created test account: {email}");
        return email;
    }

    public async Task Login(string email, string password = Password)
    {
        var page = await Page("/Identity/Account/Login");
        using var response = await PostForm("/Identity/Account/Login", new()
        {
            ["__RequestVerificationToken"] = HtmlForm.Token(page),
            ["Input.Email"] = email, ["Input.Password"] = password
        });
        Assert.Equal(HttpStatusCode.Redirect, response.StatusCode);
    }

    public async Task AuthenticateApi(string email = "admin@microsoft.com", string password = "Pass@word1")
    {
        using var response = await PostJson("/api/authenticate", new { username = email, password });
        Assert.Equal(HttpStatusCode.OK, response.StatusCode);
        var auth = await response.Content.ReadFromJsonAsync<JsonElement>();
        Assert.True(auth.GetProperty("result").GetBoolean());
        client.DefaultRequestHeaders.Authorization = new("Bearer", auth.GetProperty("token").GetString());
    }

    public async Task Add(int productId, string price = "0.01")
    {
        var page = await Page("/");
        using var response = await PostForm("/Basket", new()
        {
            ["__RequestVerificationToken"] = HtmlForm.Token(page),
            ["id"] = productId.ToString(), ["price"] = price
        });
        Assert.Equal(HttpStatusCode.Redirect, response.StatusCode);
    }

    public async Task<HttpResponseMessage> UpdateQuantity(string quantity)
    {
        var fields = HtmlForm.Inputs(await Page("/Basket"));
        fields["Items[0].Quantity"] = quantity;
        return await PostForm("/Basket/Update", fields);
    }

    public async Task<int> Checkout()
    {
        var page = await Page("/Basket/Checkout");
        using var response = await PostForm("/Basket/Checkout", HtmlForm.Inputs(page));
        Assert.Equal(HttpStatusCode.Redirect, response.StatusCode);
        Assert.Equal("/Basket/Success", response.Headers.Location?.OriginalString);
        Assert.Contains("Basket is empty", await Page("/Basket"));
        var orderId = Assert.Single(HtmlForm.OrderIds(await Page("/order/my-orders")));
        output.WriteLine($"Created order: {orderId}");
        return orderId;
    }

    public void Dispose() => client.Dispose();
}
