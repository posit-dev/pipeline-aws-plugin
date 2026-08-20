package de.taimos.pipeline.aws.cloudformation;

import lombok.Builder;
import lombok.NonNull;
import lombok.Value;

import java.time.Duration;

@Value
@Builder(toBuilder = true)
public class PollConfiguration {

	public static final PollConfiguration DEFAULT = builder()
			.timeout(Duration.ofMinutes(10))
			.pollInterval(Duration.ofSeconds(1))
			.build();

	@NonNull
	Duration timeout, pollInterval;

	/**
	 * pollInterval: 0 is documented as disabling event printing, which EventPrinter honours by
	 * skipping its loop. It says nothing about how often the underlying wait should call AWS, and a
	 * zero delay there means a tight loop against the API for the whole wait - so a non-positive
	 * interval is substituted with one second.
	 *
	 * Every positive value, including sub-second ones, is honoured exactly: pollInterval is in
	 * milliseconds, so 250 is a legal thing to ask for.
	 *
	 * Both waiting implementations go through this, so the CloudFormation waiters and the stack-set
	 * poll loops cannot drift apart on it.
	 */
	public static final Duration DISABLED_POLL_INTERVAL_SUBSTITUTE = Duration.ofSeconds(1);

	public static Duration effectivePollInterval(Duration pollInterval) {
		if (pollInterval.isZero() || pollInterval.isNegative()) {
			return DISABLED_POLL_INTERVAL_SUBSTITUTE;
		}
		return pollInterval;
	}

	public Duration getEffectivePollInterval() {
		return effectivePollInterval(this.getPollInterval());
	}

}
