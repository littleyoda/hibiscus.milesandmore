package org.jameica.hibiscus.milesandmore;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.Charset;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import com.gargoylesoftware.htmlunit.html.HtmlPage;

import de.willuhn.datasource.GenericIterator;
import de.willuhn.datasource.rmi.DBIterator;
import de.willuhn.jameica.gui.dialogs.YesNoDialog;
import de.willuhn.jameica.hbci.messaging.ImportMessage;
import de.willuhn.jameica.hbci.rmi.Konto;
import de.willuhn.jameica.hbci.rmi.Umsatz;
import de.willuhn.jameica.plugin.Plugin;
import de.willuhn.jameica.system.Application;
import de.willuhn.logging.Logger;
import de.willuhn.util.ApplicationException;

public class Utils {

	
	  // Zerlegt einen String intelligent in max. 27 Zeichen lange Stücke
	  public static String[] parse(String line)
	  {
	    if (line == null || line.length() == 0)
	      return new String[0];
	    List<String> out = new ArrayList<String>();
	    String rest = line.trim();
	    int lastpos = 0;
	    while (rest.length() > 0) {
	    	if (rest.length() < 28) {
	    		out.add(rest);
	    		rest = "";
	    		continue;
	    	}
	    	int pos = rest.indexOf(' ', lastpos + 1);
	    	boolean zulang = (pos > 28) || pos == -1;
	    	// 1. Fall: Durchgehender Text mit mehr als 27 Zeichen ohne Space
	    	if (lastpos == 0 && zulang) {
	    		out.add(rest.substring(0, 27));
	    		rest = rest.substring(27).trim();
	    		continue;
	    	} 
	    	// 2. Fall Wenn der String immer noch passt, weitersuchen
	    	if (!zulang) {
	    		lastpos = pos;
	    		continue;
	    	}
	    	// Bis zum Space aus dem vorherigen Schritt den String herausschneiden
	    	out.add(rest.substring(0, lastpos));
	    	rest = rest.substring(lastpos + 1).trim();
	    	lastpos = 0;
	    }
	    return out.toArray(new String[0]);
	  }


	public static void main(String [] args)
	{
		String[] s = new String[]{
				"1", "1 2", "123456789012345678901234567890",
				"123456789012345678901234567 890",
				"1234567890123456789012345678 90",
				"123456789012345678901234567 890",
				"123456789012345678901234567 890 123456789012345678901234567 3342",
				};
		for (String t : s) {
			System.out.println(t + ": " + Arrays.toString(parse(t)));
		}
	}

	
    public static void writePage(HtmlPage page, String s) throws Exception {
        BufferedWriter out = new BufferedWriter(new FileWriter("/tmp/" + s + ".txt"));
        out.write(page.getUrl() + "\n");
        out.write("===============================================\n");
        out.write(page.asText() + "\n");
        out.write("===============================================\n");
        out.close();
        
        out = new BufferedWriter(new FileWriter("/tmp/" + s + ".xml"));
        out.write(page.asXml());
        out.close();
    }

	public static void debug(String path, String dateiname, String options, List<String> seiten) {
		try {
			if (options.toLowerCase().contains("save")) {
				YesNoDialog d = new YesNoDialog(YesNoDialog.POSITION_CENTER);
				d.setTitle("Speichern");
				d.setText("Sollen die Debug-Informationen gespeichert werden?\n"
						+ "Ziel: " + (new File(path, dateiname + ".zip")));
				try {
					Boolean choice = (Boolean) d.open();
					if (!choice.booleanValue()) {
						return;
					}
				}	catch (Exception e) {
					return;
				}
				try { 
					ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(
							new File(path, dateiname + ".zip")));
					int i = 0;
					for (String x : seiten) {
						ZipEntry ze = new ZipEntry(dateiname + "." + i + ".html");
						zip.putNextEntry(ze);
						zip.write(x.getBytes(Charset.forName("UTF-8")));
						zip.closeEntry();
						i++;
					}
					zip.close();
				} catch (IOException e) {
					throw new ApplicationException(e);

				}
			}
		} catch (Exception e) {
			Logger.error("Zusammenstellung der Debug-Informationen fehlgeschlagen", e);
		}
	}
	
	
	public static String getWorkingDir(Class<? extends Plugin> class1) {
		return Application.getPluginLoader().getPlugin(class1).getResources().getWorkPath();
	}
	

	public static boolean equals(double a, double b) {
		   return Math.abs(a - b) <= 0.0001;
	}
	
	static boolean isBuchungBekannt(Umsatz umsatz, GenericIterator existing) throws RemoteException {
		existing.begin();
		while (existing.hasNext()) {
			Umsatz e = (Umsatz) existing.next();
			
			// Buchungen nur über Konto, Betrag, Datum und Valuta vergleichen
			if (e.getKonto().equals(umsatz.getKonto())
				&& equals(e.getBetrag(), umsatz.getBetrag())
				&& e.getDatum().equals(umsatz.getDatum())
				&& e.getValuta().equals(umsatz.getValuta())
			   ) {
				return true;
			}
		}
		return false;
	}
	
	/**
	 * Ermittelt aus einer Menge an Umsätzen den zeitlich ältesten
	 * @param fetched
	 * @return ältestes Datum ggf. NULL
	 * @throws RemoteException 
	 */
	public static Date getOldest(List<Umsatz> fetched) throws RemoteException {
		Date oldest = null;
		// Ermitteln des aeltesten abgerufenen Umsatzes, um den Bereich zu ermitteln,
		// gegen den wir aus der Datenbank abgleichen
		for (Umsatz umsatz:fetched)
		{
			if (oldest == null || umsatz.getDatum().before(oldest))
				oldest = umsatz.getDatum();
		}
		return oldest;
	}


	/**
	 * Gleicht die Übergebeben Umsätze mit den bereits bekannten Umsätzen ab und fügt neue Umsätze dem Konto hinzu
	 * @param konto
	 * @param fetched
	 * @return
	 * @throws RemoteException
	 * @throws ApplicationException
	 */
	public static boolean abgleichAlteundNeueUmsätze(Konto konto, List<Umsatz> fetched) throws RemoteException, ApplicationException {
		boolean neueUmsaetze = false;
		Date oldest = getOldest(fetched);
		DBIterator existing = konto.getUmsaetze(oldest,null);
		for (Umsatz umsatz:fetched)
		{
			if (Utils.isBuchungBekannt(umsatz, existing)) {
				continue; // haben wir schon
			}

			neueUmsaetze = true;
			
			// Neuer Umsatz. Anlegen
			umsatz.store();

			// Per Messaging Bescheid geben, dass es einen neuen Umsatz gibt. Der wird dann sofort in der Liste angezeigt
			Application.getMessagingFactory().sendMessage(new ImportMessage(umsatz));
		}
		if (neueUmsaetze) {
			korrigiereZwischensummen(konto, oldest);
		}
		return neueUmsaetze;
	}
	
	public static void korrigiereZwischensummen(Konto konto, Date oldest) throws RemoteException, ApplicationException {
		// Für alle Buchungen rückwirkend den Saldo anpassen, da teilweise nachträglich auch mal Korrekturen mit 
		// dem ursprünglichen Datum einfügt werden
		DBIterator existing = konto.getUmsaetze(oldest,null);
		double saldo = konto.getSaldo();
		while (existing.hasNext()) {
			Umsatz a = (Umsatz) existing.next();
			a.setSaldo(saldo);
			a.store();
			saldo -= a.getBetrag();
			Application.getMessagingFactory().sendMessage(new ImportMessage(a));
		}
	}

	public static String remove1310(String ausgang) {
		return remove(ausgang, "\n", "\r");
	}

	public static String remove(String ausgang, String... remove) {
		for (String r : remove) {
			ausgang = ausgang.replace(r, " ");
		}
		return ausgang.trim();
	}

}
