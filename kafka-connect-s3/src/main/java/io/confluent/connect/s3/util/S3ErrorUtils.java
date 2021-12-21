/*
 * Copyright 2021 Confluent Inc.
 *
 * Licensed under the Confluent Community License (the "License"); you may not use
 * this file except in compliance with the License.  You may obtain a copy of the
 * License at
 *
 * http://www.confluent.io/confluent-community-license
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OF ANY KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations under the License.
 */

package io.confluent.connect.s3.util;

import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonWebServiceRequest;
import com.amazonaws.retry.PredefinedRetryPolicies;
import io.confluent.connect.storage.common.util.StringUtils;
import org.apache.kafka.connect.errors.ConnectException;
import org.apache.kafka.connect.errors.RetriableException;

import java.io.IOException;

public class S3ErrorUtils {

  /**
   *
   * @param exception
   * @return
   */
  private static boolean isRetryableAwsExceptionType(Throwable exception) {
    if (exception == null) {
      return false;
    }
    if (exception instanceof IOException) {
      if (exception.equals(exception.getCause())) {
        return false;
      }
      // IOException, in many places, is passed the AWS exception
      // when it is thrown.  We recurse here to check that exception
      // for ther IOException case.  Otherwise, the IOException
      // is considered not retryable.
      // Exception: An IOException embedded within an `AmazonClientException`
      // should be passed via the `AmazonClientException` object
      // as its parent (as the SDK does), in which case, shouldRetry()
      // will often find it retryable.
      return isRetryableAwsExceptionType(exception.getCause());
    }
    if (exception instanceof AmazonClientException) {
      // The AWS SDK maintains a check for what it considers to be
      // retryable exceptions.
      return PredefinedRetryPolicies.DEFAULT_RETRY_CONDITION.shouldRetry(
          AmazonWebServiceRequest.NOOP,
          (AmazonClientException) exception,
          Integer.MAX_VALUE
      );
    }
    return false;
  }

  /**
   *
   * @param message
   * @param t
   * @return
   */
  public static ConnectException maybeRetriableConnectException(
      String message,
      Throwable t
  ) {
    // If this is already a ConnectException of some sort, just rethrow it
    if (t instanceof ConnectException) {
      return (ConnectException) t;
    }
    if (isRetryableAwsExceptionType(t)) {
      return StringUtils.isNotBlank(message)
          ? new RetriableException(message, t) : new RetriableException(t);
    }
    return StringUtils.isNotBlank(message)
        ? new ConnectException(message, t) : new ConnectException(t);
  }

  /**
   *
   * @param t
   * @return
   */
  public static ConnectException maybeRetriableConnectException(
      Throwable t
  ) {
    return maybeRetriableConnectException(null, t);
  }

}
