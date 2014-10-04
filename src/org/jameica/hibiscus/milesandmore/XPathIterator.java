package org.jameica.hibiscus.milesandmore;

import java.util.ArrayList;
import java.util.List;

import com.gargoylesoftware.htmlunit.html.DomNode;
import com.gargoylesoftware.htmlunit.html.HtmlPage;

import de.willuhn.util.ApplicationException;

public class XPathIterator<Art> {
	
	private DomNode page;
	private String xpath;
	private String description;
	public XPathIterator(DomNode page, String xpath, String description) {
		this.page = page;
		this.xpath = xpath;
		this.description = description;
	}
	
	public List<Art> get() {
		List<Art> liste = new ArrayList<Art>();
		for (Art a: (List<Art>) page.getByXPath(xpath)) {
			if (matches(a)) {
				liste.add(a);
			}
		}
		return liste;
	}
	
	public Art getOne() throws ApplicationException {
		List<Art> liste = get();
		if (liste.size() > 1) {
			throw new ApplicationException("Gewünschtes Element '" + description + "' wurde zu häufig gefunden: " + liste.size());
		}
		if (liste.size() == 0) {
			throw new ApplicationException("Gewünschtes Element '" + description + "' wurde nicht gefunden.");
		}
		return liste.get(0);
	}
	

	public boolean matches(Art x) {
		return true;
	}

}
