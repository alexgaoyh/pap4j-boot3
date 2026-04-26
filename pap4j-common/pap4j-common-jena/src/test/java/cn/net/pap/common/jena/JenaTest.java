package cn.net.pap.common.jena;

import org.apache.jena.query.*;
import org.apache.jena.rdf.model.*;
import org.apache.jena.vocabulary.VCARD;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;

public class JenaTest {

    @Test
    public void test1() {
        String uri = "http://pap.net.cn/alexgaoyh";
        Model model = ModelFactory.createDefaultModel();
        Resource resource = model.createResource(uri);

        Resource nameResourse = model.createResource();
        nameResourse.addProperty(VCARD.Given, "yihang");
        nameResourse.addProperty(VCARD.Family, "gao");

        resource.addProperty(VCARD.N, nameResourse);
        resource.addProperty(VCARD.FN, "alex gaoyihang");

        // 链式创建的实现
//        Resource alexgaoyh
//                = ModelFactory.createDefaultModel().createResource(uri)
//                .addProperty(VCARD.FN, "alex gaoyihang")
//                .addProperty(VCARD.N,
//                        model.createResource()
//                                .addProperty(VCARD.Given, "yihang")
//                                .addProperty(VCARD.Family, "gao"));


        // 三元组
        StmtIterator stmtIterator = model.listStatements();
        while (stmtIterator.hasNext()) {
            Statement statement = stmtIterator.nextStatement();
            Resource subject = statement.getSubject();
            Property predicate = statement.getPredicate();
            RDFNode object = statement.getObject();
            System.out.print(subject.toString());
            System.out.print(" " + predicate.toString() + " ");
            if (object instanceof Resource) {
                System.out.print(object.toString());
            } else {
                System.out.print("\"" + object.toString() + "\"");
            }
            System.out.println(" .");
        }

        System.out.println("-------------WRITE-------------------------------------------------------------");
        model.write(System.out, "N-TRIPLES");

        System.out.println("-------------WRITE-------------------------------------------------------------");
        model.write(System.out);

        System.out.println("------------listSubjectsWithProperty-------------------------------------------");
        ResIterator iter = model.listSubjectsWithProperty(VCARD.FN);
        while (iter.hasNext()) {
            Resource r = iter.nextResource();
            System.out.println(r.getProperty(VCARD.FN).getString());
        }

        System.out.println("----------------------QUERY----------------------------------------------------");
        String sparqlQueryString = "SELECT ?x ?p ?o WHERE { ?x ?p ?o . FILTER (?p = <http://www.w3.org/2001/vcard-rdf/3.0#FN>) }";
        Query query = QueryFactory.create(sparqlQueryString);
        QueryExecution qexec = QueryExecutionFactory.create(query, model);
        try {
            ResultSet results = qexec.execSelect();
            for (; results.hasNext(); ) {
                QuerySolution soln = results.nextSolution();
                System.out.println(soln.get("x") + "   " + soln.get("p") + "   " + soln.get("o"));
            }

        } finally {
            qexec.close();
        }

    }

    @Test
    public void test2Kuangbiao() throws Exception {
        Model model = ModelFactory.createDefaultModel();
        String fileAbsolutePath = TestResourceUtil.getFile("kuangbiao_240410145535.nt").getAbsolutePath().toString();
        model = model.read(fileAbsolutePath);

        System.out.println("----------------------QUERY--FILTER--STR(?o) != ------------------------------");
        String sparqlQueryString = "SELECT ?x ?p ?o \n" +
                "WHERE { \n" +
                "  ?x ?p ?o .\n" +
                "  FILTER (STR(?o) != \"人物\" && STR(?o) != \"组织\" && STR(?o) != \"人物/#高启强\")\n" +
                "}";
        Query query = QueryFactory.create(sparqlQueryString);
        QueryExecution qexec = QueryExecutionFactory.create(query, model);
        try {
            ResultSet results = qexec.execSelect();
            for (; results.hasNext(); ) {
                QuerySolution soln = results.nextSolution();
                System.out.println(soln.get("x") + "   " + soln.get("p") + "   " + soln.get("o"));
            }

        } finally {
            qexec.close();
        }
    }


}
