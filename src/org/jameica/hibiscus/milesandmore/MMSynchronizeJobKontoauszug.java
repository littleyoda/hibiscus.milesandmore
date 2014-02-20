package org.jameica.hibiscus.milesandmore;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.StringReader;
import java.rmi.RemoteException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import javax.annotation.Resource;

import com.gargoylesoftware.htmlunit.BrowserVersion;
import com.gargoylesoftware.htmlunit.ElementNotFoundException;
import com.gargoylesoftware.htmlunit.Page;
import com.gargoylesoftware.htmlunit.SilentCssErrorHandler;
import com.gargoylesoftware.htmlunit.TextPage;
import com.gargoylesoftware.htmlunit.ThreadedRefreshHandler;
import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.html.DomElement;
import com.gargoylesoftware.htmlunit.html.DomNodeList;
import com.gargoylesoftware.htmlunit.html.HtmlAnchor;
import com.gargoylesoftware.htmlunit.html.HtmlButton;
import com.gargoylesoftware.htmlunit.html.HtmlElement;
import com.gargoylesoftware.htmlunit.html.HtmlForm;
import com.gargoylesoftware.htmlunit.html.HtmlImageInput;
import com.gargoylesoftware.htmlunit.html.HtmlInput;
import com.gargoylesoftware.htmlunit.html.HtmlOption;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.gargoylesoftware.htmlunit.html.HtmlRadioButtonInput;
import com.gargoylesoftware.htmlunit.html.HtmlSelect;
import com.gargoylesoftware.htmlunit.html.HtmlSpan;
import com.gargoylesoftware.htmlunit.html.HtmlSubmitInput;
import com.gargoylesoftware.htmlunit.html.HtmlTable;
import com.gargoylesoftware.htmlunit.html.HtmlTableRow;

import de.willuhn.datasource.GenericIterator;
import de.willuhn.jameica.hbci.Settings;
import de.willuhn.jameica.hbci.messaging.ImportMessage;
import de.willuhn.jameica.hbci.messaging.SaldoMessage;
import de.willuhn.jameica.hbci.rmi.Konto;
import de.willuhn.jameica.hbci.rmi.Umsatz;
import de.willuhn.jameica.hbci.synchronize.jobs.SynchronizeJob;
import de.willuhn.jameica.hbci.synchronize.jobs.SynchronizeJobKontoauszug;
import de.willuhn.jameica.system.Application;
import de.willuhn.logging.Logger;
import de.willuhn.util.ApplicationException;
import de.willuhn.util.I18N;

/**
 * Implementierung des Kontoauszugsabruf fuer AirPlus.
 * Von der passenden Job-Klasse ableiten, damit der Job gefunden wird.
 */
public class MMSynchronizeJobKontoauszug extends SynchronizeJobKontoauszug implements MMSynchronizeJob
{
	private final static I18N i18n = Application.getPluginLoader().getPlugin(Plugin.class).getResources().getI18N();

	@Resource
	private MMSynchronizeBackend backend = null;

	private DateFormat df = new SimpleDateFormat("dd/MM/yyyy", Locale.GERMAN);
	/**
	 * @see org.jameica.hibiscus.barclaystg.AirPlusSynchronizeJob#execute()
	 */
	@Override
	public void execute() throws Exception
	{
		Konto konto = (Konto) this.getContext(CTX_ENTITY); // wurde von AirPlusSynchronizeJobProviderKontoauszug dort abgelegt

		Logger.info("Rufe Umsä�tze ab f�r " + backend.getName());

		////////////////
		String username = konto.getMeta(MMSynchronizeBackend.PROP_USERNAME, null);
		String password = konto.getMeta(MMSynchronizeBackend.PROP_PASSWORD, null);
		if (username == null || username.length() == 0)
			throw new ApplicationException(i18n.tr("Bitte geben Sie Ihren Karten-Nummer in den Synchronisationsoptionen ein"));

		if (password == null || password.length() == 0)
			throw new ApplicationException(i18n.tr("Bitte geben Sie Ihr Passwort in den Synchronisationsoptionen ein"));

		Logger.info("username: " + username);
		////////////////


		List<Umsatz> fetched = doOneAccount(konto, username, password);

		Date oldest = null;

		// Ermitteln des aeltesten abgerufenen Umsatzes, um den Bereich zu ermitteln,
		// gegen den wir aus der Datenbank abgleichen
		for (Umsatz umsatz:fetched)
		{
			if (oldest == null || umsatz.getDatum().before(oldest))
				oldest = umsatz.getDatum();
		}


		// Wir holen uns die Umsaetze seit dem letzen Abruf von der Datenbank
		GenericIterator existing = konto.getUmsaetze(oldest,null);
		for (Umsatz umsatz:fetched)
		{
			if (existing.contains(umsatz) != null)
				continue; // haben wir schon

			// Neuer Umsatz. Anlegen
			umsatz.store();

			// Per Messaging Bescheid geben, dass es einen neuen Umsatz gibt. Der wird dann sofort in der Liste angezeigt
			Application.getMessagingFactory().sendMessage(new ImportMessage(umsatz));
		}

		konto.store();

		// Und per Messaging Bescheid geben, dass das Konto einen neuen Saldo hat
		Application.getMessagingFactory().sendMessage(new SaldoMessage(konto));
	}

	public List<Umsatz> doOneAccount(Konto konto, String username, String password) throws Exception {
		List<Umsatz> umsaetze = new ArrayList<Umsatz>();

		final WebClient webClient = new WebClient(BrowserVersion.INTERNET_EXPLORER_8);
		webClient.setCssErrorHandler(new SilentCssErrorHandler());
		webClient.setRefreshHandler(new ThreadedRefreshHandler());

		// Login-Page und Login
		HtmlPage page = webClient.getPage("http://www.miles-and-more.com/online/portal/mam/de/homepage?l=de&cid=18002");
		List<HtmlForm> forms = (List<HtmlForm>) page.getByXPath( "//form[@name='mamrd_loginbox']");
		if (forms.size() != 1) {
			throw new ApplicationException(i18n.tr("Konnte das Login-Formular nicht finden."));
		}
		HtmlForm form = forms.get(0);
		form.getInputByName("userid").setValueAttribute(username);
		form.getInputByName("password").setValueAttribute(password);
		HtmlAnchor button = (HtmlAnchor) page.getElementById("loginbtn");
		page = button.click();
//		Utils.writePage(page, "MMLogin");
		page = webClient.getPage("https://www.miles-and-more.com/online/myportal/mam/de/account/account_statement?nodeid=2221453&l=de&cid=18002");
	//	Utils.writePage(page, "MMAccountStatement");
		List<HtmlTable> tabellen = (List<HtmlTable>) page.getByXPath( "//table");
		HtmlTable tab = null;
		for (HtmlTable h : tabellen) {
			if (h.asText().startsWith("Datum")) {
				tab = h;
				break;
			}
		}
		if (tab == null) {
			throw new ApplicationException(i18n.tr("Konnte die Punktetabelle nicht finden."));
		}
		String[] current = null;
		for (int i = 1; i < tab.getRows().size() - 1; i++) {
			HtmlTableRow zeile = tab.getRows().get(i);
			if (zeile.getCells().size() != 4) {
				continue;
			}
			if (!zeile.getCells().get(0).asText().trim().equals("")) {
				store(current, umsaetze, konto);
				current = new String[4];
				for (int j = 0; j < 4; j++) {
					current[j] = "";
				}
			}
			for (int j = 0; j < 4; j++) {
				String s = remove1310(zeile.getCells().get(j).asText());
				if (s.isEmpty()) {
					continue;
				}
				if (!current[j].isEmpty()) {
					current[j] += "\n";
				}
				current[j] += s; 
			}

		}
		HtmlTableRow summenzeile = tab.getRows().get(tab.getRows().size() - 1);
		konto.setSaldo(string2float(summenzeile.getCell(2).asText().trim()));
		store(current, umsaetze, konto);

		// Logout-Funktion erstmal deaktiviert, da es zu einem Absturz von HTMLUNIT führt
		
//		List<HtmlAnchor> logout = (List<HtmlAnchor>) page.getByXPath( "//a[@onclick='reportLogout();']");
//		if (logout.size() != 1) {
//			throw new ApplicationException(i18n.tr("Konnte den Logout-Link nicht finden."));
//		}
//		page = logout.get(0).click(); 
		webClient.closeAllWindows();
		return umsaetze;
	}




	private void store(String[] current, List<Umsatz> umsaetze, Konto konto) throws RemoteException, ParseException {
		if (current == null) {
			return;
		}
		if (current[2].equals("")) {
			current[2] = "0"; // Sonderfall. Es gibt teilweise Einträge ohne Punkte
		}
		Umsatz newUmsatz = (Umsatz) Settings.getDBService().createObject(Umsatz.class,null);
		newUmsatz.setKonto(konto);
		newUmsatz.setBetrag(string2float(current[2]));
		newUmsatz.setDatum(df.parse(current[0]));
		newUmsatz.setValuta(df.parse(current[0]));
		String zweck = current[1];
		if (!current[3].isEmpty()) {
			zweck += "\nStatusmeilen: " + current[3].replace(" ", "").trim();
		}
		newUmsatz.setWeitereVerwendungszwecke(Utils.parse(zweck));
		umsaetze.add(newUmsatz);
	}

	/**
	 * - Tausender Punkte entfernen
	 * - Komma durch Punkt ersetzen
	 * @param s
	 * @return
	 */
	public static float string2float(String s) {
		try {
			return Float.parseFloat(s.replace(".", "").replace(",", "."));
		} catch (Exception e) {
			throw new RuntimeException("Cannot convert " + s + " to float");
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




