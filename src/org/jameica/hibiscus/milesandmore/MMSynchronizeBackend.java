package org.jameica.hibiscus.milesandmore;

import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.List;

import de.willuhn.annotation.Lifecycle;
import de.willuhn.annotation.Lifecycle.Type;
import de.willuhn.jameica.hbci.rmi.Konto;
import de.willuhn.jameica.hbci.synchronize.AbstractSynchronizeBackend;
import de.willuhn.jameica.hbci.synchronize.jobs.SynchronizeJob;
import de.willuhn.jameica.system.Application;
import de.willuhn.logging.Logger;
import de.willuhn.util.I18N;
import de.willuhn.util.ProgressMonitor;

/**
 * Implementierung eines Sync-Backends.
 */
@Lifecycle(Type.CONTEXT)
public class MMSynchronizeBackend extends AbstractSynchronizeBackend<MMSynchronizeJobProvider>
{
  private final static I18N i18n = Application.getPluginLoader().getPlugin(Plugin.class).getResources().getI18N();

  final static String PROP_PASSWORD = "Passwort";
  final static String PROP_OPTIONS = "Optionen (kann leer bleiben)";
  
  /**
   * @see de.willuhn.jameica.hbci.synchronize.AbstractSynchronizeBackend#createJobGroup(de.willuhn.jameica.hbci.rmi.Konto)
   */
  @Override
  protected JobGroup createJobGroup(Konto k)
  {
    return new MMTgJobGroup(k);
  }
  
  /**
   * @see de.willuhn.jameica.hbci.synchronize.AbstractSynchronizeBackend#getJobProviderInterface()
   */
  @Override
  protected Class<MMSynchronizeJobProvider> getJobProviderInterface()
  {
    return MMSynchronizeJobProvider.class;
  }
  
  /**
   * @see de.willuhn.jameica.hbci.synchronize.AbstractSynchronizeBackend#getPropertyNames(de.willuhn.jameica.hbci.rmi.Konto)
   */
  @Override
  public List<String> getPropertyNames(Konto konto)
  {
    // Hier koennen wir eigene Optionen definieren, die in den Synchronisationseinstellungen
    // des Kontos in Hibiscus angezeigt werden sollen. Sie werden als Meta-Properties im
    // Konto gespeichert.
    
    // Das kann z.Bsp. Benutzername und Passwort fuer den Webzugang bei AirPlus sein.
    
    try
    {
      // Nur Offline-Konten.
      if (konto == null || konto.hasFlag(Konto.FLAG_DISABLED))
        return null;
      
      List<String> result = new ArrayList<String>();
      result.add(PROP_PASSWORD);
      result.add(PROP_OPTIONS);
      return result;
    }
    catch (RemoteException re)
    {
      Logger.error("unable to determine property-names",re);
      return null;
    }
  }
  
  /**
   * @see de.willuhn.jameica.hbci.synchronize.SynchronizeBackend#supports(java.lang.Class, de.willuhn.jameica.hbci.rmi.Konto)
   */
  @Override
  public boolean supports(Class<? extends SynchronizeJob> type, Konto konto)
  {
    boolean b = super.supports(type,konto);
    if (!b)
      return false;
    
    try
    {
      
      // Checken, ob das ein AirPlus-Konto ist
      // Muss in Hibiscus als "Offline-Konto" angelegt worden sein.
      // Kann man z.Bsp. anhand der BLZ festmachen. Oder irgend ein anderes Merkmal,
      // welches nur bei den AirPlus-Konten in Hibiscus existiert.
      if (((konto.getBLZ().equals("0000000")
    		  || konto.getBLZ().equals("0")) && 
    		  (konto.getUnterkonto().toLowerCase().replace(" ", "").equals("miles&more")
    		   || konto.getUnterkonto().toLowerCase().replace(" ", "").equals("milesandmore")))
    	  || (konto.getBackendClass() != null && konto.getBackendClass().equals(getClass().toString())))

        return true;
    }
    catch (RemoteException re)
    {
      Logger.error("unable to check for Miles&More support",re);
    }
    return false;
  }
  

  /**
   * @see de.willuhn.jameica.hbci.synchronize.SynchronizeBackend#getName()
   */
  @Override
  public String getName()
  {
    return i18n.tr("Miles and More Tagesgeld");
  }
  
  /**
   * Hier findet die eigentliche Ausfuehrung des Jobs statt.
   */
  protected class MMTgJobGroup extends JobGroup
  {
    /**
     * ct.
     * @param k
     */
    protected MMTgJobGroup(Konto k)
    {
      super(k);
    }

    /**
     * @see de.willuhn.jameica.hbci.synchronize.AbstractSynchronizeBackend.JobGroup#sync()
     */
    @Override
    protected void sync() throws Exception
    {
      ////////////////////////////////////////////////////////////////////
      // lokale Variablen
      ProgressMonitor monitor = worker.getMonitor();
      String kn               = this.getKonto().getLongName();
      
      int step = 100 / worker.getSynchronization().size();
      ////////////////////////////////////////////////////////////////////
      
      try
      {
        this.checkInterrupted();

        monitor.log(" ");
        monitor.log(i18n.tr("Synchronisiere Konto: {0}",kn));

        Logger.info("processing jobs");
        for (SynchronizeJob job:this.jobs)
        {
          this.checkInterrupted();
          
          MMSynchronizeJob j = (MMSynchronizeJob) job;
          j.execute();
          
          monitor.addPercentComplete(step);
        }
      }
      catch (Exception e)
      {
        throw e;
      }
    }
    
  }

}


