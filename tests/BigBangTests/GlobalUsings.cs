global using System.Net;
global using System.Net.Http.Json;
global using System.Text.Json;
global using Xunit;
global using Xunit.Abstractions;
global using BigBangTests.Support;

// Catalog changes and cache reads share one real application and database.
[assembly: CollectionBehavior(DisableTestParallelization = true)]
