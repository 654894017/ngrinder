<#include "../common/ngrinder_macros.ftl">
<div class="modal hide fade" id="upload_file_modal" xmlns="http://www.w3.org/1999/html">
    <div class="modal-header">
        <a class="close" data-dismiss="modal">&times;&nbsp;</a>
        <h4><@spring.message "script.action.convert.uploadHAR2Script"/></h4>
    </div>

    <div class="tabbable modal-body" id="input_form" style="display:; overflow-x:hidden; overflow-y:hidden;">
        <label class="checkbox" style="position:relative; margin-left:20px">
            <input type="checkbox" name="removeIncludeStaticCall" value="true">
            Remove Include Static Resource Call
        </label>
        <ul class="nav nav-tabs" id="input_tab">
            <li class="active">
                <a href="#file_type"><@spring.message "script.action.convert.file"/></a>
            </li>
            <li>
                <a href="#textarea_type"><@spring.message "script.action.convert.textarea"/></a>
            </li>
        </ul>
        <div class="tab-content">
            <div class="tab-pane active" id="file_type">
                <form class="form-horizontal" method="post" target="_self" action="/script/convert"
                      id="upload_file_form" enctype="multipart/form-data" name="uploadFileForm">
				<@control_group name="fileInput" label_message_key="script.info.file">
                    <div rel='upload_file_modal_popover' data-html='true'
                         title='<@spring.message "script.message.upload.title"/>'
                         data-content='<@spring.message "script.message.upload.content"/>'>
                        <input type="file" class="input-file" id="file_input" name="fileInput"/>
                        <span class="help-inline"></span>
                    </div>
				</@control_group>
                </form>
            </div>
            <div class="tab-pane" id="textarea_type" style="height: 340px;">
                <form method="post" target="_self" action="/script/convert" id="upload_text_form" name="uploadTextForm">
				<@control_group name="har_textarea" label_message_key="HAR Content Area">
                    <textarea id="har_textarea" name="har_textarea" title="har_textarea" class="tx_area" style="resize: none; height:247px; width:98%;"></textarea>
				</@control_group>
                </form>
            </div>
        </div>
    </div>

    <div class="tabbable modal-body" id="result_form" style="display:none; overflow-x:hidden; overflow-y:hidden;">
        <ul class="nav nav-tabs" id="result_tab">
            <li class="active">
                <a href="#groovy_type"><@spring.message "script.action.convert.groovy"/></a>
            </li>
            <li>
                <a href="#jython_type"><@spring.message "script.action.convert.jython"/></a>
            </li>
        </ul>
        <div class="tab-content">

            <div class="tab-pane active" style="height: 400px;" id="groovy_type">
                <textarea name="groovy_textarea" class="tx_area" style="resize: none; height:300px; width:98%;"></textarea>
            </div>

            <div class="tab-pane" style="height: 400px;" id="jython_type">
                <textarea name="jython_textarea" class="tx_area" style="resize: none; height:300px; width:98%;"></textarea>
            </div>
        </div>
    </div>
    <div class="modal-footer">
		<button class="btn btn-primary" id="upload_file_button"><@spring.message "script.action.convert"/></button>
        <button class="btn" data-dismiss="modal"><@spring.message "common.button.cancel"/></button>
    </div>
</div>

<script>
    $(document).ready(function() {

        //validate textarea
        $("#upload_text_form").validate({
            rules: {
                har_textarea: {
                    required: true
                },
            },
            messages: {
                har_textarea: {
                    required: "<@spring.message "common.message.validate.empty"/>"
                }
            },
            errorClass: "help-inline",
            errorElement: "span",
            highlight: function (element, errorClass, validClass) {
                $('#upload_text_form').find('.control-group').addClass('error').removeClass('success');
//                $('#upload_text_form').find('.control-group').removeClass('success');
            },
            unhighlight: function (element, errorClass, validClass) {
                $('#upload_text_form').find('.control-group').removeClass('error').addClass('success');
//                $('#upload_text_form').find('.control-group').addClass('success');
            }
        });

        var $formType = '#file_type';
        $("div[rel='upload_file_modal_popover']").popover({trigger: 'focus', container:'#upload_file_modal'});
        $("#upload_file_button").click(function() {
            var data = new FormData();
            data.append("removeIncludeStaticCall", $('input:checkbox[name="removeIncludeStaticCall"]').is(":checked"));
            if ($formType == '#textarea_type') {
                if ($('#upload_text_form').valid()) {
                    data.append('harContent', $('#har_textarea').val());
                    ajaxFormData(data,'/script/convert/textarea');
                }
            } else {
                var $file = $('#file_input');
                if (checkEmptyByObj($file)) {
                    markInput($file, false, "<@spring.message "common.message.validate.empty"/>");
                    return;
                }

                data.append( 'uploadFile', $('#file_input')[0].files[0] );
                ajaxFormData(data,'/script/convert/file');
            }
        });

        //show tab navi
        var $inputTab = $('#input_tab');
        $inputTab.find('a').click(function (e) {
            e.preventDefault();
            $formType = $(this).attr('href');
            $(this).tab('show');
        });
        $inputTab.find('a:first').tab('show');

        var $resultTab = $('#result_tab');
        $resultTab.find('a').click(function (e) {
            e.preventDefault();
            $(this).tab('show');
        });
        $resultTab.find('a:first').tab('show');

    });

    function ajaxFormData(data,url){
        showProgressBar("<@spring.message 'script.action.convert.trans'/>");
        $.ajax({
            url: url,
            processData: false,
            contentType: false,
            data: data,
            type: 'POST',
            success: function(result){
                $('#input_form').hide();
                $('#result_form').show();
                $('#upload_file_button').hide();
                $('.control-group').removeClass('error');
                $('textarea[name="groovy_textarea"]').val(result['groovy']);
                $('textarea[name="jython_textarea"]').val(result['jython']);
            },
            complete:function () {
                hideProgressBar();
            },error:function () {
                showErrorMsg("<@spring.message 'common.error.error'/>");
            }
        });
    }

</script>
