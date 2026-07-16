/**
 * Provides immutable HTTP exchange events and their JSON rendering contract.
 *
 * <p>Build request and response facts with the exchange value types, then use
 * {@link HttpExchangeEventWriter} to render the fixed schema version 1 event.
 * A {@link JsonValue} must contain exactly one complete JSON value.
 *
 * @since 0.1
 */
package io.github.summerwenlabs.log.mask.http.exchange;
