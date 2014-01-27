<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%--@elvariable id="tradingPair" type="com.springapp.cryptoexchange.database.model.TradingPair"--%>
<%--@elvariable id="history" type="java.util.List<com.springapp.cryptoexchange.webapi.AbstractConvertService.MarketHistory>"--%>
<%--@elvariable id="depth" type="com.springapp.cryptoexchange.webapi.AbstractConvertService.Depth"--%>
<html>
<head>
    <%@include file="scripts.jsp"%>
    <title></title>
</head>
<body>
    <div id="content">
        <h1>${tradingPair.description}</h1>
        <p>Last: ${tradingPair.lastPrice}</p>
        <p>Low: ${tradingPair.dayLow}</p>
        <p>High: ${tradingPair.dayHigh}</p>

        <h3>Trade:</h3>
        <form action="trade">

        </form>

        <h3>Depth:</h3>
        <h4>Buy:</h4>
        <c:forEach var="order" items="${depth.buyOrders}">
            ${order}
        </c:forEach>

        <h4>Sell:</h4>
        <c:forEach var="order" items="${depth.sellOrders}">
            ${order}
        </c:forEach>

        <h3>Last trades:</h3>
        <c:forEach var="trade" items="${history}">
            ${trade}
        </c:forEach>
    </div>
</body>
</html>
