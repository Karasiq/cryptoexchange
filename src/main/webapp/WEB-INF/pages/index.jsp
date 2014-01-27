<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ taglib prefix="form" uri="http://www.springframework.org/tags/form"%>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<html>
<head>
    <%@include file="scripts.jsp"%>
    <title>CryptoExchange</title>
    <script>
        $(function() {
            $('.content-link').click(function() {
                $.get($(this).attr('href'), function (data) {
                    var $content_div = $("#content");
                    $content_div.html(data).html($content_div.children("#content").html());
                });
                return false;
            });
        });
    </script>
</head>
<body>
    <%--@elvariable id="tradingPairs" type="java.util.List<com.springapp.cryptoexchange.database.model.TradingPair>"--%>
    <c:forEach var="tradingPair" items="${tradingPairs}">
        <a class="content-link" href="market/${tradingPair.id}">${tradingPair.name}</a>
    </c:forEach>
    <div id="content"></div>
</body>
</html>