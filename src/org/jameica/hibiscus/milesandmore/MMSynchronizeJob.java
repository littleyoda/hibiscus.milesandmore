package org.jameica.hibiscus.milesandmore;

import de.willuhn.jameica.hbci.synchronize.jobs.SynchronizeJob;

/**
 * Marker-Interface fuer die vom Plugin unterstuetzten Jobs.
 */
public interface MMSynchronizeJob extends SynchronizeJob
{
  // Hier koennen wir jetzt eigene Funktionen definieren, die dann von
  // der JobGroup im AirPlusSynchronizeBackend ausgefuehrt werden.
  
  /**
   * Fuehrt den Job aus.
   * @throws Exception
   */
  public void execute() throws Exception;

}


