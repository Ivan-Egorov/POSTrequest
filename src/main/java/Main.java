import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ExecutorService;

public class Main {
    public static void main(String[] args) throws InterruptedException {
        CrptApi crptApi = new CrptApi(TimeUnit.SECONDS, 10);
        CrptApi.Document document = crptApi.createDocument();
        document.setDESCRIPTION_participantInn("participantInn");
        document.setDoc_id("doc_id");
        document.setDoc_status("doc_status");
        document.setDoc_type("doc_type");
        document.setImportRequest("importRequest");
        document.setOwner_inn("owner_inn");
        document.setParticipant_inn("participant_inn");
        document.setProducer_inn("producer_inn");
        document.setProduction_date("production_date");
        document.setProduction_type_OWN_PRODUCTION();
        document.setPRODUCTS_certificate_document_CONFORMITY_CERTIFICATE();
        document.setPRODUCTS_certificate_document_date("certificate_document_date");
        document.setPRODUCTS_certificate_document_number("certificate_document_number");
        document.setPRODUCTS_owner_inn("owner_inn");
        document.setPRODUCTS_producer_inn("producer_inn");
        document.setPRODUCTS_production_date("production_date");
        document.setPRODUCTS_tnved_code("tnved_code");
        document.setPRODUCTS_uit_code("uit_code");
        document.setPRODUCTS_uitu_code("uitu_code");

        ExecutorService executorService = Executors.newFixedThreadPool(5);
        for (int i = 0; i < 15; i++) {
            executorService.submit(() -> {
                crptApi.introduceGoods(document, "signature");
            });
        }
        executorService.shutdown();
        executorService.awaitTermination(2, TimeUnit.MINUTES);
    }
}
