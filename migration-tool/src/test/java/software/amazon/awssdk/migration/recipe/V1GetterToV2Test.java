package software.amazon.awssdk.migration.recipe;

import static org.openrewrite.java.Assertions.java;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnJre;
import org.junit.jupiter.api.condition.JRE;
import org.openrewrite.java.Java8Parser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import software.amazon.awssdk.migration.internal.recipe.V1GetterToV2;

public class V1GetterToV2Test implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipes(new ChangeSdkType(), new NewClassToBuilderPattern(), new V1GetterToV2());
        spec.parser(Java8Parser.builder().classpath("sqs",
                                                    "aws-java-sdk-sqs",
                                                    "sqs",
                                                    "sdk-core"));
    }

    @Test
    @EnabledOnJre({JRE.JAVA_8})
    void v1ModelClassGetter_isRewrittenToFluent() {
        rewriteRun(
            java(
                "import com.amazonaws.services.sqs.AmazonSQS;\n"
                + "import com.amazonaws.services.sqs.AmazonSQSClient;\n"
                + "import com.amazonaws.services.sqs.model.ReceiveMessageRequest;\n"
                + "import com.amazonaws.services.sqs.model.ReceiveMessageResult;\n"
                + "import com.amazonaws.services.sqs.model.Message;\n"
                + "\n"
                + "public class SqsExample {\n"
                + "    public static void main(String[] args) {\n"
                + "        AmazonSQS sqs = new AmazonSQSClient();\n"
                + "        ReceiveMessageRequest request = new ReceiveMessageRequest().withQueueUrl(\"url\");\n"
                + "        ReceiveMessageResult receiveMessage = sqs.receiveMessage(request);\n"
                + "        List<Message> messages = receiveMessage.getMessages();\n"
                + "        Message message = receiveMessage.getMessages().get(0);\n"
                + "    }\n"
                + "}\n",
                "import software.amazon.awssdk.services.sqs.SqsClient;\n"
                + "import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;\n"
                + "import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;\n"
                + "import software.amazon.awssdk.services.sqs.model.Message;\n"
                + "\n"
                + "public class SqsExample {\n"
                + "    public static void main(String[] args) {\n"
                + "        SqsClient sqs = SqsClient.builder().build();\n"
                + "        ReceiveMessageRequest request = ReceiveMessageRequest.builder().queueUrl(\"url\").build();\n"
                + "        ReceiveMessageResponse receiveMessage = sqs.receiveMessage(request);\n"
                + "        List<Message> messages = receiveMessage.messages();\n"
                + "        Message message = receiveMessage.messages().get(0);\n"
                + "    }\n"
                + "}"
            )
        );
    }

    @Test
    @EnabledOnJre({JRE.JAVA_8})
    void nonV1ModelClass_shouldNotChangeGetter() {
        rewriteRun(
            java(
                "import java.util.Locale;\n"
                + "\n"
                + "public class NonV1ModelClassExample {\n"
                + "    public static void main(String[] args) {\n"
                + "        Locale locale = Locale.getDefault();\n"
                + "        String path = System.getenv(\"PATH\");\n"
                + "        String className = String.class.getName();\n"
                + "    }\n"
                + "}\n",
                "import java.util.Locale;\n"
                + "\n"
                + "public class NonV1ModelClassExample {\n"
                + "    public static void main(String[] args) {\n"
                + "        Locale locale = Locale.getDefault();\n"
                + "        String path = System.getenv(\"PATH\");\n"
                + "        String className = String.class.getName();\n"
                + "    }\n"
                + "}"
            )
        );
    }
}