package com.borjaglez.cqrs;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.borjaglez.cqrs.naming.CqrsMessage;

/**
 * Index of the message classes a registry handles, by the logical name the naming strategy gives
 * them (the {@code @CqrsMessage} name for annotated messages). Transports use it to find the local
 * class of an incoming message from its name, so the class of the producer does not have to exist
 * in the consumer and may be renamed or moved.
 *
 * <p>Only classes annotated with {@link CqrsMessage} are indexed: the name of any other class is
 * its kebab-case simple name, which is not qualified by service, so the same name may belong to a
 * class of another service on a shared topic. Those messages keep being resolved by class name.
 *
 * <p>A name shared by two classes is ambiguous and resolves to nothing: the transport then falls
 * back to the class name the message carries, as it did before names were indexed.
 */
public final class MessageNameIndex {

  private static final Log LOG = LogFactory.getLog(MessageNameIndex.class);

  private final Map<String, Class<?>> classes = new ConcurrentHashMap<>();
  private final Set<String> ambiguous = ConcurrentHashMap.newKeySet();

  /**
   * Indexes {@code messageClass} under {@code messageName}. A {@code null} name, or a class without
   * {@link CqrsMessage}, is ignored.
   */
  public void add(String messageName, Class<?> messageClass) {
    if (messageName == null || !messageClass.isAnnotationPresent(CqrsMessage.class)) {
      return;
    }
    Class<?> existing = classes.putIfAbsent(messageName, messageClass);
    if (existing != null && existing != messageClass && ambiguous.add(messageName)) {
      LOG.warn(
          "Message name '"
              + messageName
              + "' is shared by "
              + existing.getName()
              + " and "
              + messageClass.getName()
              + "; incoming messages with this name are resolved by the class name they carry."
              + " Give each class its own @CqrsMessage name");
    }
  }

  /** The class indexed under {@code messageName}; empty when unknown, ambiguous or null. */
  public Optional<Class<?>> find(String messageName) {
    if (messageName == null || ambiguous.contains(messageName)) {
      return Optional.empty();
    }
    return Optional.ofNullable(classes.get(messageName));
  }
}
