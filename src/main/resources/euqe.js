$(document).ready(function(){

		// hide the VNC stuff
		disconnect();
		
 		$("#closeVNC").click(function(event){
 			disconnect();
 		});
 	
       $(".vncCapable").click(function(event){
       		$('#screen').show();
       		$('#screen').css({ opacity: 0.7, 'width':$(document).width(),'height':$(document).height(),'overflow':'hidden'});
			
			
       		$("#VNC_status_bar").show();
        	$("#VNC_canvas").show();
       		var ip =$(this).attr('ip');
		var hub =$(this).attr('hub');
		var pwd =$(this).attr('pwd');
       		var x = $(this).attr('display');
       		
       		$("#vnc").html("starting connection to "+ip+':'+x);
       		
       		
       		
       		
			$.ajax({
  				url: "?ip="+ip+"&x="+x,
  				type : 'POST',
  				context: document.body,
  				success: function(data, textStatus, jqXHR){ 
  					 					
    				 $("#vnc").html("loading...");
    				 connect2(hub,jqXHR.responseText,pwd);
  				},
  				error: function(jqXHR, textStatus, errorThrown){
  					$("#vnc").html(jqXHR.responseText);	
  				}
		});
		
         event.preventDefault();
       });
     });
        


/*jslint white: false */
		/*global window, $, Util, RFB, */
        "use strict";
 
    	var rfb;
    	var serverUp = false;
 
        function setPassword() {
            rfb.sendPassword($D('password_input').value);
            return false;
        }
        
        function disconnect(){
        	 $("#VNC_status_bar").hide();
        	 $("#VNC_canvas").hide();
        	 $('#screen').hide();
        	 
       		 if (rfb) {
        		rfb.disconnect();
        	}
        }
        
        
        function connect2(ip,port,password) {
        	if (rfb) {
        		rfb.disconnect();
        	}
            rfb = new RFB({'target':   document.getElementById('VNC_canvas'),
                           'encrypt':      false,
                           'true_color':   true,
                           'local_cursor': true,
                           'shared':       true,
                           'updateState':  updateState2});
                           
            rfb.connect(ip,port,password);
        }
        
        function sendCtrlAltDel() {
            rfb.sendCtrlAltDel();
            return false;
        }
        
        
        
        function updateState2(rfb, state, oldstate, msg) {
        	//alert('new state '+state);
            var s, sb, cad, klass;
            s = $D('VNC_status');
            sb = $D('VNC_status_bar');
            cad = $D('sendCtrlAltDelButton');
            switch (state) {
                case 'failed':
                case 'fatal':
                    klass = "VNC_status_error";
                    break;
                case 'normal':
                    klass = "VNC_status_normal";
                    break;
                case 'disconnected':
                case 'loaded':
                    klass = "VNC_status_normal";
                    break;
                case 'password':
                    msg = '<form onsubmit="return setPassword();"';
                    msg += '  style="margin-bottom: 0px">';
                    msg += 'Password Required: ';
                    msg += '<input type=password size=10 id="password_input" class="VNC_status">';
                    msg += '<\/form>';
                    klass = "VNC_status_warn";
                    break;
                default:
                    klass = "VNC_status_warn";
                   
            }
 
            if (state === "normal") { cad.disabled = false; }
            else                    { cad.disabled = true; }
 
            if (typeof(msg) !== 'undefined') {
                sb.setAttribute("class", klass);
                s.innerHTML = msg;
            }
        };
