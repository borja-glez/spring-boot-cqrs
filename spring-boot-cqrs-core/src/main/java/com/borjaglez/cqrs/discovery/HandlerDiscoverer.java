package com.borjaglez.cqrs.discovery;

/** Strategy for discovering CQRS handlers from Spring beans. */
public interface HandlerDiscoverer {

  /**
   * Inspects the given bean for CQRS handler annotations and registers any discovered handlers.
   *
   * @param bean the Spring bean instance
   * @param beanName the name of the bean in the application context
   */
  void discover(Object bean, String beanName);
}
