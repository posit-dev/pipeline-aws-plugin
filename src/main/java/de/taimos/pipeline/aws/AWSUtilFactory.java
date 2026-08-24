package de.taimos.pipeline.aws;

import software.amazon.awssdk.services.cloudformation.CloudFormationClient;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.transfer.s3.S3TransferManager;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.transfer.TransferManager;
import com.amazonaws.services.s3.transfer.TransferManagerBuilder;
import de.taimos.pipeline.aws.cloudformation.CloudFormationStack;
import de.taimos.pipeline.aws.cloudformation.stacksets.CloudFormationStackSet;
import de.taimos.pipeline.aws.cloudformation.stacksets.SleepStrategy;
import hudson.model.TaskListener;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

import java.util.function.Function;
import java.util.function.Supplier;

public class AWSUtilFactory {

	private static Function<String, CloudFormationStack> stackSupplier;
	private static Function<String, CloudFormationStackSet> stackSetSupplier;
	private static Supplier<TransferManager> transferManagerSupplier;
	private static Supplier<S3TransferManager> v2TransferManagerSupplier;


	@Restricted(NoExternalUse.class)
	public static void setStackSupplier(Function<String, CloudFormationStack> supplier) {
		stackSupplier = supplier;
	}

	@Restricted(NoExternalUse.class)
	public static void setStackSetSupplier(Function<String, CloudFormationStackSet> supplier) {
		stackSetSupplier = supplier;
	}

	public static CloudFormationStack newCFStack(CloudFormationClient client, String stack, TaskListener listener) {
		if (stackSupplier != null) {
			return stackSupplier.apply(stack);
		}
		return new CloudFormationStack(client, stack, listener);
	}

	public static CloudFormationStackSet newCFStackSet(CloudFormationClient client,
			String stack, TaskListener listener, SleepStrategy sleepStrategy) {
		if (stackSetSupplier != null) {
			return stackSetSupplier.apply(stack);
		}
		return new CloudFormationStackSet(client, stack, listener, sleepStrategy);
	}

	public static TransferManager newTransferManager(AmazonS3 s3Client) {
		if (transferManagerSupplier != null) {
			return transferManagerSupplier.get();
		}
		return TransferManagerBuilder.standard()
				.withS3Client(s3Client)
				.build();
	}

	public static void setTransferManagerSupplier(Supplier<TransferManager> tfSupplier) {
		transferManagerSupplier = tfSupplier;
	}

	/**
	 * The v2 transfer manager owns the async client it is given: closing it closes the client, which
	 * is why callers use try-with-resources on the manager alone.
	 */
	public static S3TransferManager newV2TransferManager(S3AsyncClient s3Client) {
		if (v2TransferManagerSupplier != null) {
			return v2TransferManagerSupplier.get();
		}
		return S3TransferManager.builder()
				.s3Client(s3Client)
				.build();
	}

	@Restricted(NoExternalUse.class)
	public static void setV2TransferManagerSupplier(Supplier<S3TransferManager> tfSupplier) {
		v2TransferManagerSupplier = tfSupplier;
	}
}
