import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import java.io.IOException;
import java.time.LocalDate;
import java.util.LinkedList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.Semaphore;

public class CrptApi {
    private String token;
    private final long timeSpan;
    private final Semaphore semaphore;
    private final LinkedList<Long> requestList;

    // Конструктор с указанием количества запросов в определенный интервал времени
    public CrptApi(TimeUnit timeUnit, int requestLimit) {
        if (requestLimit < 0) {
            throw new IllegalArgumentException("Количество запросов не может быть отрицательным");
        }
        timeSpan = timeUnit.toMillis(1);
        semaphore = new Semaphore(requestLimit);
        requestList = new LinkedList<>();
    }

    // Проверка количества запросов
    private boolean checkRequestCount() {
        long now = System.currentTimeMillis();
        synchronized (requestList) {
            // Удаление старых запросов из списка
            while (!requestList.isEmpty() && (now - requestList.peekFirst() > timeSpan)) {
                requestList.pollFirst();
                semaphore.release();
            }
            // Проверка ограничения на количество запросов к API
            if (semaphore.tryAcquire()) {
                requestList.addLast(now);
                return true;
            }
            // Если не удалось отправить запрос из-за ограничения на количество запросов к API
            System.out.printf("Запрос %s отклонён в связи с превышением количества запросов\n",
                    Thread.currentThread().getName());
            return false;
        }
    }

    // http запрос (ввод в оборот товара, произведенного в РФ)
    public boolean introduceGoods (Document document, String signature) {
        if (checkRequestCount()) {
            String json = getDocJson(document).toString();
            StringEntity entity = new StringEntity(json, ContentType.APPLICATION_JSON);
            CloseableHttpClient httpClient = HttpClientBuilder.create().build();
            HttpPost post = new HttpPost("https://ismp.crpt.ru/api/v3/auth/cert/key");
            post.addHeader("Accept", "*/*");
            post.addHeader("Authorization", String.format("Bearer <%s>", getToken(signature)));
            post.setEntity(entity);
            try {
                CloseableHttpResponse httpResponse = httpClient.execute(post);
                // здесь можно произвести дополнительные действия с ответом сервера
                System.out.println(httpResponse.getStatusLine());
                String statusCode = String.valueOf(httpResponse.getStatusLine().getStatusCode());
                if (statusCode.charAt(0) == 2) {
                    System.out.println("Товар успешно введён в оборот.");
                }
                httpClient.close();
                httpResponse.close();
            } catch (IOException e) {
                System.out.println(e.getMessage());
            }
            return true;
        }
        return false;
    }

    // Аутентификация
    private synchronized String getToken(String signature) {
        // Здесь будет проверка токена, получение пары uuid - data, подписание data УКЭПом, получение актуального токена
            if (token == null) {
                token = "token";
            }
            return token;
    }

    // Создание Json объекта, по переданному для запроса, Java документу
    private JSONObject getDocJson(Document document) {
        if (!document.check()) {
            System.out.println("Не все обязательные поля заполнены!");
            throw new IllegalArgumentException();
        }
        JSONObject doc = new JSONObject();
        JSONObject description = new JSONObject();
        description.put("participantInn", document.getParticipant_inn());
        doc.put("description", description);
        doc.put("doc_id", document.getDoc_id());
        doc.put("doc_status", document.getDoc_status());
        doc.put("doc_type", document.getDoc_type());
        if (isFilled(document.getImportRequest())) {
            doc.put("importRequest", document.getImportRequest());
        }
        doc.put("owner_inn", document.getOwner_inn());
        doc.put("participant_inn", document.getParticipant_inn());
        doc.put("producer_inn", document.getProducer_inn());
        doc.put("production_date", document.getProduction_date());
        doc.put("production_type", document.getProduction_type());
        JSONObject products = new JSONObject();
        if (isFilled(document.getPRODUCTS_certificate_document())) {
            products.put("certificate_document", document.getPRODUCTS_certificate_document());
        }
        if (isFilled(document.getPRODUCTS_certificate_document_date())) {
            products.put("certificate_document_date", document.getPRODUCTS_certificate_document_date());
        }
        if (isFilled(document.getPRODUCTS_certificate_document_number())) {
            products.put("certificate_document_number", document.getPRODUCTS_certificate_document_number());
        }
        products.put("owner_inn", document.getPRODUCTS_owner_inn());
        products.put("producer_inn", document.getPRODUCTS_producer_inn());
        if (!document.getPRODUCTS_production_date().equals(document.getProduction_date())) {
            products.put("production_date", document.getPRODUCTS_production_date());
        }
        products.put("tnved_code", document.getPRODUCTS_tnved_code());
        if (isFilled(document.getPRODUCTS_uit_code())) {
            products.put("uit_code", document.getPRODUCTS_uit_code());
        }
        if (isFilled(document.getPRODUCTS_uitu_code())) {
            products.put("uitu_code", document.getPRODUCTS_uitu_code());
        }
        JSONArray array = new JSONArray();
        array.add(products);
        doc.put("products", array);
        doc.put("reg_date", document.getReg_date());
        doc.put("reg_number", document.getReg_number());
        return doc;
    }

    // Проверка обязательных полей
    private boolean isFilled(String string) {
        return string != null && !string.isEmpty();
    }

    // Создание файла с данными для ввода в оборот товара
    public Document createDocument() {
        Document document = new Document();
        document.reg_date = String.valueOf(LocalDate.now());
        document.reg_number = "reg_number"; // Здесь логика по генераци регистрационного номера документа
        return document;
    }

    // Файл с данными для ввода в оборот товара
    public class Document {
        private String DESCRIPTION_participantInn;
        private String doc_id;
        private String doc_status;
        private String doc_type;
        private String importRequest; // Необязательное поле
        private String owner_inn;
        private String participant_inn;
        private String producer_inn;
        private String production_date;
        private String production_type; // Возможные значения: OWN_PRODUCTION, CONTRACT_PRODUCTION
        private String PRODUCTS_certificate_document; // Необязательное поле. Возможные значения: CONFORMITY_CERTIFICATE, CONFORMITY_DECLARATION
        private String PRODUCTS_certificate_document_date; // Необязательное поле
        private String PRODUCTS_certificate_document_number; // Необязательное поле
        private String PRODUCTS_owner_inn;
        private String PRODUCTS_producer_inn;
        private String PRODUCTS_production_date; // Присутствует в запросе, если значение отличается от production_date
        private String PRODUCTS_tnved_code;
        private String PRODUCTS_uit_code; // Обязательный, если не указан uitu
        private String PRODUCTS_uitu_code; // Обязательный, если не указан uit
        private String reg_date; // Присваивается автоматически
        private String reg_number; // Присваивается автоматически

        // Закрытый конструктор
        private Document() {
        }

        // Проверка обязательных полей
        public boolean check() {
            if (isFilled(DESCRIPTION_participantInn) && isFilled(doc_id) && isFilled(doc_status) &&
                    isFilled(doc_type) && isFilled(owner_inn) && isFilled(participant_inn) &&
                    isFilled(producer_inn) && isFilled(production_date) && isFilled(production_type) &&
                    isFilled(PRODUCTS_owner_inn) && isFilled(PRODUCTS_producer_inn) &&
                    isFilled(PRODUCTS_production_date) && isFilled(PRODUCTS_tnved_code) &&
                    (isFilled(PRODUCTS_uit_code) || isFilled(PRODUCTS_uitu_code))) {
                return true;
            } else {
                return false;
            }
        }

        // Сеттеры
        public void setDESCRIPTION_participantInn(String DESCRIPTION_participantInn) {
            this.DESCRIPTION_participantInn = DESCRIPTION_participantInn;
        }

        public void setDoc_id(String doc_id) {
            this.doc_id = doc_id;
        }

        public void setDoc_status(String doc_status) {
            this.doc_status = doc_status;
        }

        public void setDoc_type(String doc_type) {
            this.doc_type = doc_type;
        }

        public void setImportRequest(String importRequest) {
            this.importRequest = importRequest;
        }

        public void setOwner_inn(String owner_inn) {
            this.owner_inn = owner_inn;
        }

        public void setParticipant_inn(String participant_inn) {
            this.participant_inn = participant_inn;
        }

        public void setProducer_inn(String producer_inn) {
            this.producer_inn = producer_inn;
        }

        public void setProduction_date(String production_date) {
            this.production_date = production_date;
        }

        public void setProduction_type_OWN_PRODUCTION() {
            production_type = "OWN_PRODUCTION - Собственное производство";
        }

        public void setProduction_type_CONTRACT_PRODUCTION() {
            production_type = "OWN_PRODUCTION - Собственное производство";
        }

        public void setPRODUCTS_certificate_document_CONFORMITY_CERTIFICATE() {
            PRODUCTS_certificate_document = "\"CONFORMITY_CERTIFICATE\" - сертификат соответствия";
        }

        public void setPRODUCTS_certificate_document_CONFORMITY_DECLARATION() {
            PRODUCTS_certificate_document = "\"CONFORMITY_DECLARATION\" - декларация соответствия";
        }

        public void setPRODUCTS_certificate_document_date(String PRODUCTS_certificate_document_date) {
            this.PRODUCTS_certificate_document_date = PRODUCTS_certificate_document_date;
        }

        public void setPRODUCTS_certificate_document_number(String PRODUCTS_certificate_document_number) {
            this.PRODUCTS_certificate_document_number = PRODUCTS_certificate_document_number;
        }

        public void setPRODUCTS_owner_inn(String PRODUCTS_owner_inn) {
            this.PRODUCTS_owner_inn = PRODUCTS_owner_inn;
        }

        public void setPRODUCTS_producer_inn(String PRODUCTS_producer_inn) {
            this.PRODUCTS_producer_inn = PRODUCTS_producer_inn;
        }

        public void setPRODUCTS_production_date(String PRODUCTS_production_date) {
            this.PRODUCTS_production_date = PRODUCTS_production_date;
        }

        public void setPRODUCTS_tnved_code(String PRODUCTS_tnved_code) {
            this.PRODUCTS_tnved_code = PRODUCTS_tnved_code;
        }

        public void setPRODUCTS_uit_code(String PRODUCTS_uit_code) {
            this.PRODUCTS_uit_code = PRODUCTS_uit_code;
        }

        public void setPRODUCTS_uitu_code(String PRODUCTS_uitu_code) {
            this.PRODUCTS_uitu_code = PRODUCTS_uitu_code;
        }

        // Геттеры
        public String getDESCRIPTION_participantInn() {
            return DESCRIPTION_participantInn;
        }

        public String getDoc_id() {
            return doc_id;
        }

        public String getDoc_status() {
            return doc_status;
        }

        public String getDoc_type() {
            return doc_type;
        }

        public String getImportRequest() {
            return importRequest;
        }

        public String getOwner_inn() {
            return owner_inn;
        }

        public String getParticipant_inn() {
            return participant_inn;
        }

        public String getProducer_inn() {
            return producer_inn;
        }

        public String getProduction_date() {
            return production_date;
        }

        public String getProduction_type() {
            return production_type;
        }

        public String getPRODUCTS_certificate_document() {
            return PRODUCTS_certificate_document;
        }

        public String getPRODUCTS_certificate_document_date() {
            return PRODUCTS_certificate_document_date;
        }

        public String getPRODUCTS_certificate_document_number() {
            return PRODUCTS_certificate_document_number;
        }

        public String getPRODUCTS_owner_inn() {
            return PRODUCTS_owner_inn;
        }

        public String getPRODUCTS_producer_inn() {
            return PRODUCTS_producer_inn;
        }

        public String getPRODUCTS_production_date() {
            return PRODUCTS_production_date;
        }

        public String getPRODUCTS_tnved_code() {
            return PRODUCTS_tnved_code;
        }

        public String getPRODUCTS_uit_code() {
            return PRODUCTS_uit_code;
        }

        public String getPRODUCTS_uitu_code() {
            return PRODUCTS_uitu_code;
        }

        public String getReg_date() {
            return reg_date;
        }

        public String getReg_number() {
            return reg_number;
        }
    }
}