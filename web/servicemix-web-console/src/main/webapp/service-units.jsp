<%--
    Licensed to the Apache Software Foundation (ASF) under one or more
    contributor license agreements.  See the NOTICE file distributed with
    this work for additional information regarding copyright ownership.
    The ASF licenses this file to You under the Apache License, Version 2.0
    (the "License"); you may not use this file except in compliance with
    the License.  You may obtain a copy of the License at
   
    http://www.apache.org/licenses/LICENSE-2.0
   
    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
--%>
<html>
<head>
<title>Service Units</title>
</head>
<body>
<h2>Service Units</h2>

<c:choose>
<c:when test="${empty requestContext.serviceUnits}">
	No service units found.
</c:when>
<c:otherwise>
<table id="serviceUnits" class="sortable autostripe">
  <thead>
    <tr>
      <th>Name</th>
      <th>Status</th>
      <th>Component</th>
      <th>Service Assembly</th>
    </tr>
  </thead>
  <tbody>
    <c:forEach items="${requestContext.serviceUnits}" var="row">
      <tr>
        <td><a href="service-unit.jsp?name=${row.name}">${row.name}</a></td>
        <td>${row.status}</td>
        <td><a href="component.jsp?name=${row.component.name}">${row.component.name}</a></td>
        <td><a href="service-assembly.jsp?name=${row.serviceAssembly.name}">${row.serviceAssembly.name}</a></td>
      </tr>
    </c:forEach>
  </tbody>
</table>
</c:otherwise>
</c:choose>

</body>
</html>
	
