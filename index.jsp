<%-- 
    Document   : index
    Created on : Nov 18, 2015, 6:55:20 PM
    Author     : snehabhadbhade
--%>

<%@page contentType="text/html" pageEncoding="UTF-8"%>
<!DOCTYPE html>
<html>
    <head>
        <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
        <title>JSP Page</title>
    </head>
    <body>
       <div>
<h1>NLP Kickstart</h1>
<form action="METANLP" method="POST">
  <table>
         <tr><td colspan=2>
            <br>Please enter your text here:<br><br>
                <textarea valign=top name="stext" style="width: 400px; height: 8em" rows=31 cols=7></textarea>
         </td></tr>

         <tr><td align=left>
            <input type="submit"/>
         </td></tr>
  </table>
</FORM>
</div>
    </body>
</html>
