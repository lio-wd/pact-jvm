package au.com.dius.pact.consumer.junit.pactproviderrule;

import au.com.dius.pact.core.model.annotations.Pact;
import au.com.dius.pact.consumer.junit.PactVerification;
import au.com.dius.pact.consumer.PactVerificationResult;
import au.com.dius.pact.consumer.dsl.PactDslWithProvider;
import au.com.dius.pact.consumer.junit.exampleclients.ConsumerClient;
import au.com.dius.pact.core.matchers.BodyMismatch;
import au.com.dius.pact.core.matchers.Mismatch;
import au.com.dius.pact.core.model.RequestResponsePact;
import org.junit.Rule;
import org.junit.Test;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;

public class PactMultiProviderTest {

    private static final String NAME_LARRY_JSON = "{\"name\": \"larry\"}";
    @Rule
    public TestFailureProviderRule mockTestProvider = new TestFailureProviderRule("test_provider", this);

    @Rule
    public TestFailureProviderRule mockTestProvider2 = new TestFailureProviderRule("test_provider2", this);

    @Pact(provider="test_provider", consumer="test_consumer")
    public RequestResponsePact createFragment(PactDslWithProvider builder) {
        Map<String, String> headers = new HashMap<String, String>();
        headers.put("testreqheader", "testreqheadervalue");

        return builder
            .given("good state")
            .uponReceiving("PactProviderTest test interaction")
                .path("/")
                .method("GET")
                .headers(headers)
            .willRespondWith()
                .status(200)
                .headers(headers)
                .body("{\"responsetest\": true, \"name\": \"harry\"}")
            .uponReceiving("PactProviderTest second test interaction")
                .method("OPTIONS")
                .headers(headers)
                .path("/second")
                .body("")
            .willRespondWith()
                .status(200)
                .headers(headers)
                .body("")
            .toPact();
    }

    @Pact(provider="test_provider2", consumer="test_consumer")
    public RequestResponsePact createFragment2(PactDslWithProvider builder) {
        return builder
                .given("good state")
                .uponReceiving("PactProviderTest test interaction")
                .path("/")
                .method("PUT")
                .body(NAME_LARRY_JSON)
                .willRespondWith()
                .status(200)
                .body("{\"responsetest\": true, \"name\": \"larry\"}")
                .toPact();
    }

    @Test
    @PactVerification({"test_provider", "test_provider2"})
    public void allPass() throws IOException {
      mockTestProvider.validateResultWith((result, t) -> {
        assertThat(t, is(nullValue()));
        assertThat(result, is(instanceOf(PactVerificationResult.Ok.class)));
      });
      doTest("/", NAME_LARRY_JSON);
    }

    @Test
    @PactVerification({"test_provider", "test_provider2"})
    public void consumerTestFails() throws IOException, InterruptedException {
      mockTestProvider.validateResultWith((result, t) -> {
        assertThat(t, is(instanceOf(AssertionError.class)));
        assertThat(t.getMessage(), is("Pact Test function failed with an exception: Oops"));
        assertThat(result, is(instanceOf(PactVerificationResult.Error.class)));
        PactVerificationResult.Error error = (PactVerificationResult.Error) result;
        assertThat(error.getError(), is(instanceOf(RuntimeException.class)));
        assertThat(error.getError().getMessage(), is("Oops"));
        assertThat(error.getMockServerState(), is(instanceOf(PactVerificationResult.Ok.class)));
      });
      doTest("/", NAME_LARRY_JSON);
      throw new RuntimeException("Oops");
    }

    @Test
    @PactVerification(value = {"test_provider", "test_provider2"})
    public void provider1Fails() throws IOException, InterruptedException {
        mockTestProvider.validateResultWith((result, t) -> {
          assertThat(t, is(instanceOf(AssertionError.class)));
          assertThat(t.getMessage(), startsWith("The following mismatched requests occurred:\nUnexpected Request:\n\tmethod: GET\n\tpath: /abc"));
          assertThat(result, is(instanceOf(PactVerificationResult.Mismatches.class)));
          PactVerificationResult.Mismatches error = (PactVerificationResult.Mismatches) result;
          assertThat(error.getMismatches(), hasSize(1));
          PactVerificationResult result1 = error.getMismatches().get(0);
          assertThat(result1, is(instanceOf(PactVerificationResult.UnexpectedRequest.class)));
          PactVerificationResult.UnexpectedRequest unexpectedRequest = (PactVerificationResult.UnexpectedRequest) result1;
          assertThat(unexpectedRequest.getRequest().getPath(), is("/abc"));
        });
        doTest("/abc", NAME_LARRY_JSON);
    }

    @Test
    @PactVerification(value = {"test_provider", "test_provider2"})
    public void provider2Fails() throws IOException {
      mockTestProvider2.validateResultWith((result, t) -> {
        assertThat(t, is(instanceOf(AssertionError.class)));
        assertThat(t.getMessage(), is("The following mismatched requests occurred:\n" +
          "body - $.name: Expected 'larry' (String) but received 'farry' (String)"));
        assertThat(result, is(instanceOf(PactVerificationResult.Mismatches.class)));
        PactVerificationResult.Mismatches error = (PactVerificationResult.Mismatches) result;
        assertThat(error.getMismatches(), hasSize(1));
        PactVerificationResult result1 = error.getMismatches().get(0);
        assertThat(result1, is(instanceOf(PactVerificationResult.PartialMismatch.class)));
        PactVerificationResult.PartialMismatch error1 = (PactVerificationResult.PartialMismatch) result1;
        assertThat(error1.getMismatches(), hasSize(1));
        Mismatch mismatch = error1.getMismatches().get(0);
        assertThat(mismatch, is(instanceOf(BodyMismatch.class)));
      });
      doTest("/", "{\"name\": \"farry\"}");
    }

    @Test
    @PactVerification(value = {"test_provider", "test_provider2"})
    public void bothprovidersFail() throws IOException {
      mockTestProvider.validateResultWith((result, t) -> {
        assertThat(t, is(instanceOf(AssertionError.class)));
        assertThat(t.getMessage(), startsWith("The following mismatched requests occurred:\nUnexpected Request:\n\tmethod: GET\n\tpath: /abc"));
        assertThat(result, is(instanceOf(PactVerificationResult.Mismatches.class)));
        PactVerificationResult.Mismatches error = (PactVerificationResult.Mismatches) result;
        assertThat(error.getMismatches(), hasSize(1));
        PactVerificationResult result1 = error.getMismatches().get(0);
        assertThat(result1, is(instanceOf(PactVerificationResult.UnexpectedRequest.class)));
        PactVerificationResult.UnexpectedRequest unexpectedRequest = (PactVerificationResult.UnexpectedRequest) result1;
        assertThat(unexpectedRequest.getRequest().getPath(), is("/abc"));
      });
      mockTestProvider2.validateResultWith((result, t) -> {
        assertThat(t, is(instanceOf(AssertionError.class)));
        assertThat(t.getMessage(), is("The following mismatched requests occurred:\n" +
          "body - $.name: Expected 'larry' (String) but received 'farry' (String)"));
        assertThat(result, is(instanceOf(PactVerificationResult.Mismatches.class)));
        PactVerificationResult.Mismatches error = (PactVerificationResult.Mismatches) result;
        assertThat(error.getMismatches(), hasSize(1));
        PactVerificationResult result1 = error.getMismatches().get(0);
        assertThat(result1, is(instanceOf(PactVerificationResult.PartialMismatch.class)));
        PactVerificationResult.PartialMismatch error1 = (PactVerificationResult.PartialMismatch) result1;
        assertThat(error1.getMismatches(), hasSize(1));
        Mismatch mismatch = error1.getMismatches().get(0);
        assertThat(mismatch, is(instanceOf(BodyMismatch.class)));
      });
      doTest("/abc", "{\"name\": \"farry\"}");
    }

    private void doTest(String path, String json) throws IOException {
      ConsumerClient consumerClient = new ConsumerClient(mockTestProvider.getUrl());
      consumerClient.options("/second");
      try {
        consumerClient.getAsMap(path, "");
      } catch (IOException e) {
      }
      try {
        new ConsumerClient(mockTestProvider2.getUrl()).putAsMap("/", json);
      } catch (IOException e) {
      }
    }
}
