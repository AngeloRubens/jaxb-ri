package bench;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlValue;
import tools.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import tools.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import tools.jackson.dataformat.xml.annotation.JacksonXmlRootElement;
import tools.jackson.dataformat.xml.annotation.JacksonXmlText;

import java.util.ArrayList;
import java.util.List;

/**
 * Benchmark models. Catalog/Book is the simdxml-java ObjectBindingGcBenchmark fixture (4, 32,
 * 256 books = 191 B, 1,407 B, 11,575 B). FieldCatalog/NamespacedCatalog are the simdxml-java
 * BeanAccessBenchmark "fields" and "namespaced" shapes. Each model carries the Jackson
 * annotations that make XmlMapper bind the same document; the benchmarks verify the bound graph.
 */
public final class Models {
    private Models() {}

    public static final String NS = "urn:bench";

    @XmlRootElement(name = "catalog")
    @JacksonXmlRootElement(localName = "catalog")
    @XmlAccessorType(XmlAccessType.FIELD)
    public static class Catalog {
        @XmlElement(name = "book")
        @JacksonXmlProperty(localName = "book")
        @JacksonXmlElementWrapper(useWrapping = false)
        public List<Book> books = new ArrayList<>();
    }

    @XmlAccessorType(XmlAccessType.FIELD)
    public static class Book {
        @XmlAttribute
        @JacksonXmlProperty(isAttribute = true, localName = "id")
        public int id;
        @XmlValue
        @JacksonXmlText
        public String title;
    }

    @XmlRootElement(name = "catalog")
    @JacksonXmlRootElement(localName = "catalog")
    @XmlAccessorType(XmlAccessType.FIELD)
    public static class FieldCatalog {
        @XmlElement(name = "row")
        @JacksonXmlProperty(localName = "row")
        @JacksonXmlElementWrapper(useWrapping = false)
        public List<Row> rows = new ArrayList<>();
    }

    @XmlAccessorType(XmlAccessType.FIELD)
    public static class Row {
        @XmlElement public int id;
        @XmlElement public String name;
        @XmlElement public double score;
        @XmlElement public boolean active;
    }

    @XmlRootElement(name = "catalog", namespace = NS)
    @JacksonXmlRootElement(localName = "catalog", namespace = NS)
    @XmlAccessorType(XmlAccessType.FIELD)
    public static class NamespacedCatalog {
        @XmlElement(name = "row", namespace = NS)
        @JacksonXmlProperty(localName = "row", namespace = NS)
        @JacksonXmlElementWrapper(useWrapping = false)
        public List<NamespacedRow> rows = new ArrayList<>();
    }

    @XmlAccessorType(XmlAccessType.FIELD)
    public static class NamespacedRow {
        @XmlElement(namespace = NS) @JacksonXmlProperty(namespace = NS) public int id;
        @XmlElement(namespace = NS) @JacksonXmlProperty(namespace = NS) public String name;
        @XmlElement(namespace = NS) @JacksonXmlProperty(namespace = NS) public double score;
        @XmlElement(namespace = NS) @JacksonXmlProperty(namespace = NS) public boolean active;
    }

    public static Catalog catalog(int count) {
        Catalog catalog = new Catalog();
        for (int i = 0; i < count; i++) {
            Book book = new Book();
            book.id = i;
            book.title = "XML book " + i + " — SIMDXML";
            catalog.books.add(book);
        }
        return catalog;
    }

    public static FieldCatalog fieldRows(int count) {
        FieldCatalog c = new FieldCatalog();
        for (int i = 0; i < count; i++) {
            Row r = new Row();
            r.id = i; r.name = "row " + i; r.score = i + 0.5; r.active = true;
            c.rows.add(r);
        }
        return c;
    }

    public static NamespacedCatalog namespacedRows(int count) {
        NamespacedCatalog c = new NamespacedCatalog();
        for (int i = 0; i < count; i++) {
            NamespacedRow r = new NamespacedRow();
            r.id = i; r.name = "row " + i; r.score = i + 0.5; r.active = true;
            c.rows.add(r);
        }
        return c;
    }

    /** Canonical string form of any of the graphs, used to verify every implementation binds the same data. */
    public static String describe(Object o) {
        StringBuilder b = new StringBuilder(o.getClass().getSimpleName()).append('[');
        if (o instanceof Catalog c) for (Book x : c.books) b.append(x.id).append('/').append(x.title).append(';');
        else if (o instanceof FieldCatalog c) for (Row x : c.rows) b.append(x.id).append('/').append(x.name).append('/').append(x.score).append('/').append(x.active).append(';');
        else if (o instanceof NamespacedCatalog c) for (NamespacedRow x : c.rows) b.append(x.id).append('/').append(x.name).append('/').append(x.score).append('/').append(x.active).append(';');
        else throw new IllegalArgumentException(String.valueOf(o));
        return b.append(']').toString();
    }
}
